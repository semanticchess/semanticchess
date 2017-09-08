package de.daug.semanticchess.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import de.daug.semanticchess.Annotation.PosTagger;
import de.daug.semanticchess.Annotation.Token;
import de.daug.semanticchess.Parser.Helper.Classes;
import de.daug.semanticchess.Parser.Helper.ColorAllocator;
import de.daug.semanticchess.Parser.Helper.CustomNer;
import de.daug.semanticchess.Parser.Helper.Entity;
import de.daug.semanticchess.Parser.Helper.Flipper;
import de.daug.semanticchess.Parser.Helper.Options;
import de.daug.semanticchess.Parser.Helper.OptionsAllocator;
import de.daug.semanticchess.Parser.Helper.Resource;
import edu.stanford.nlp.ling.WordTag;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.Morphology;

/**
 * parses the user query to a sequence to find a suitable sparql query
 */
public class Parser {
	private List<Token> tokens = new ArrayList<Token>();
	private String query;

	private List<Entity> entities = new ArrayList<Entity>();
	private List<Classes> classes = new ArrayList<Classes>();
	private List<Resource> resources = new ArrayList<Resource>();
	private String options = "";

	private ChessVocabulary vocabulary = new ChessVocabulary();

	int index;

	private boolean isBlack = false;
	private boolean isWhite = false;
	private boolean isDecisive = false;
	private boolean isUnion = false;
	private boolean isNumber = false;
	private boolean isOrdinal = false;
	private boolean isRound = false;

	private String sequence;

	public Parser(String query) {
		this.query = query;

		PosTagger tagger = new PosTagger();
		StanfordCoreNLP pipeline = tagger.getPipeline();
		tagger.setQuery(query);
		tagger.setDocument(tagger.setAnnotator(pipeline, tagger.getQuery()));
		tagger.initAnnotations();

		List<Token> tokens = tagger.getTokens();

		CustomNer cNer = new CustomNer();
		tokens = cNer.stemming(tokens);
		tokens = cNer.checkChessVocabulary(tokens);
		tokens = cNer.checkElo(tokens);

		this.tokens = tokens;

		collectEntities(tokens);
		if (isBlack || isWhite) {
			injectColor();
		}

		resultChecker();
		if (isUnion()) {
			makeUnion();
		}

		if (!isUnion) {
			this.sequence = "_" + resources.size() + "" + classes.size() + "" + entities.size() + "0";
		} else {
			this.sequence = "_" + resources.size() + "" + classes.size() + "" + entities.size() + "1";
		}

		OptionsAllocator optAlloc = new OptionsAllocator(tokens);
		Options options = null;
		int limitProperty = -1;
		int offsetProperty = -1;
		String orderByProperty = "";
		if (isNumber) {
			limitProperty = optAlloc.findLimit();
			options = new Options(limitProperty, 0);
			this.options = options.toString();

		} else if (isOrdinal && !isRound) {
			offsetProperty = optAlloc.findOffset();
			orderByProperty = optAlloc.findOrderBy();

			options = new Options(10000, offsetProperty, orderByProperty);
			this.options = options.toString();

			classes.add(new Classes(classes.size() + 1, "?date", "date", 99));

		} else {
			options = new Options();
			this.options = options.toString();
		}

	}

	public static void main(String[] args) {
		String query = "1st game by Magnus Carlsen";

		Parser p = new Parser(query);

		System.out.println(p.getTokens().toString());
		System.out.println(p.getEntities().toString());
		System.out.println(p.getClasses().toString());
		System.out.println(p.getResources().toString());
	}

	public void collectEntities(List<Token> tokens) {
		for (index = 0; index < tokens.size(); index++) {
			String word = tokens.get(index).getWord();
			String ne = tokens.get(index).getNe();
			String pos = tokens.get(index).getPos();

			switch (ne) {
			case "PERSON":
				addEntityOrClass(word, ne, "white|prop:black");
				break;
			case "MISC":
				addEntityOrClass(word, ne, "");
				break;
			case "LOCATION":
				addEntityOrClass(word, ne, "site");
				break;
			case "ORGANIZATION":
				addEntityOrClass(word, ne, "event");
				break;
			case "DATE":
				addEntityOrClass(word, ne, "date");
				break;
			case "ORDINAL":
				if ((index + 1) < tokens.size() && tokens.get(index + 1).getNe().equals("round")) {
					isRound = true;
					index += 1;
					addEntityOrClass(word.replaceAll("\\D+", ""), ne, "round");
				} else {
					isOrdinal = true;
				}

				// TODO count events, etc
				break;
			case "NUMBER":
				isNumber = true;
				break;
			case "game":
				// TODO als Spezialfall, konkurrierend mit anderen res
				// TODO bei eco, opening, event,... flag für game ressource
				resources.add(new Resource((resources.size() + 1), "?game", "ChessGame", index));
				break;
			case "eco":
				addEntityOrClass(word, ne, "eco");
				break;
			case "elo":
				addEntityOrClass(word, ne, "whiteelo|prop:blackelo");
				break;
			case "black":
				isBlack = true;
				break;
			case "white":
				isWhite = true;
				break;
			case "1-0":
				isDecisive = true;
				addEntityOrClass(ne, ne, "result");
				break;
			case "0-1":
				// TODO siehe 1-0
				isDecisive = true;
				addEntityOrClass(ne, ne, "result");
				break;
			case "1/2-1/2":
				// TODO siehe 1-0
				addEntityOrClass(ne, ne, "result");
				break;
			case "event":
				addEntityOrClass(word, ne, "event");
				break;
			case "opening":
				addEntityOrClass(word, ne, "eco");
				break;
			case "moves":
				// TODO moves?
			default:
				break;

			}

		}
	}

	public void injectColor() {
		ColorAllocator ca = new ColorAllocator(tokens);
		List<Integer> personPositions = ca.getPersonPositions();
		int[] personHasColor = ca.allocateColor();
		String newNe = tokens.get(personHasColor[0]).getNe();

		if (isWhite) {
			for (Entity e : entities) {
				if (e.getEndPosition() == personHasColor[0]) {
					e.setPropertyName("prop:white");
				} else if (e.getEndPosition() != personHasColor[0]
						&& e.getPropertyName().equals("prop:white|prop:black")) {
					e.setPropertyName("prop:black");
				}
			}
			for (Classes c : classes) {
				if (c.getPosition() == personHasColor[0]) {
					c.setPropertyName("prop:white");
				} else if (c.getPosition() != personHasColor[0]
						&& c.getPropertyName().equals("prop:white|prop:black")) {
					c.setPropertyName("prop:black");
				}
			}

		} else if (isBlack) {
			for (Entity e : entities) {
				if (e.getEndPosition() == personHasColor[0]) {
					e.setPropertyName("prop:black");
				} else if (e.getEndPosition() != personHasColor[0]
						&& e.getPropertyName().equals("prop:white|prop:black")) {
					e.setPropertyName("prop:white");
				}
			}
			for (Classes c : classes) {
				if (c.getPosition() == personHasColor[0]) {
					c.setPropertyName("prop:black");
				} else if (c.getPosition() != personHasColor[0]
						&& c.getPropertyName().equals("prop:white|prop:black")) {
					c.setPropertyName("prop:white");
				}
			}
		}
	}

	public void resultChecker() {
		Stack<String> colors = new Stack<String>();
		String result = "";
		boolean isFlipped = false;

		if (isDecisive && (isBlack || isWhite)) {
			for (Entity e : entities) {
				if (e.getPropertyName().equals("prop:white") || e.getPropertyName().equals("prop:black")) {
					colors.push(e.getPropertyName());
				}
				if (e.getPropertyName().equals("prop:result")) {
					result = e.getEntityName();
				}
			}
			for (Classes c : classes) {
				if (c.getPropertyName().equals("prop:white") || c.getPropertyName().equals("prop:black")) {
					colors.push(c.getPropertyName());
				}
			}

			if (colors.size() > 1) {
				if (colors.peek().equals("prop:white")) {
					if (result.equals("'0-1'")) {
						result = "'1-0'";
						isFlipped = true;
					} else if (result.equals("'1-0'")) {
						result = "'0-1'";
						isFlipped = true;
					}
				}
			} else if (colors.size() == 1) {
				if (colors.peek().equals("prop:black")) {
					if (result.equals("'0-1'")) {
						result = "'1-0'";
						isFlipped = true;
					} else if (result.equals("'1-0'")) {
						result = "'0-1'";
						isFlipped = true;
					}
				}
			}
			System.out.println("R: " + result);

			if (isFlipped) {
				for (Entity e : entities) {
					if (e.getPropertyName().equals("prop:result")) {
						e.setEntityName(result);
					}
				}
			}
		} else if (isDecisive && (!isBlack || !isWhite)) {
			for (Entity e : entities) {
				if (e.getPropertyName().equals("prop:result")) {
					result = e.getEntityName();
				}
			}
			if (result.equals("'1-0'") || result.equals("'0-1'")) {
				isUnion = true;
				boolean isFirst = true;
				for (Entity e : entities) {
					if (e.getPropertyName().equals("prop:white|prop:black") && isFirst) {
						e.setPropertyName("prop:white");
						isFirst = false;
					} else if (e.getPropertyName().equals("prop:white|prop:black") && !isFirst) {
						e.setPropertyName("prop:black");
					}
				}
			}
		}

	}

	public void addEntityOrClass(String word, String ne, String property) {
		int startPosition = index;
		while ((index + 1) < tokens.size() && tokens.get(index + 1).getNe().equals(ne)) {
			word += " " + tokens.get(index + 1).getWord();
			index += 1;
		}
		int endPosition = index;

		String entity = vocabulary.INVERSED_PROPERTIES.get(word);

		if (entity != null && entity != "1-0" && entity != "0-1" && entity != "1/2-1/2") {
			classes.add(new Classes(classes.size() + 1, "?" + word, property, endPosition));
		} else {
			entities.add(new Entity(entities.size() + 1, "'" + word + "'", property, startPosition, endPosition));
		}
	}

	public void makeUnion() {
		Flipper flipper = new Flipper();
		String newPropertyName = "";
		String newEntityName = "";

		String Label;
		int counter = 0;

		List<Entity> tempEntities = new ArrayList<Entity>();

		for (Entity e : entities) {
			counter += 1;
			newPropertyName = flipper.toFlip(e.getPropertyName());
			newEntityName = flipper.toFlip(e.getEntityName());

			tempEntities.add(new Entity(tempEntities.size() + 1, newEntityName, newPropertyName.replace("prop:", ""),
					e.getStartPosition(), e.getEndPosition()));
		}

		for (Entity t : tempEntities) {
			entities.add(new Entity(entities.size() + 1, t.getEntityName(), t.getPropertyName().replace("prop:", ""),
					t.getStartPosition(), t.getEndPosition()));
		}

		// TODO auch für classes
	}

	List<Token> getTokens() {
		return tokens;
	}

	void setTokens(List<Token> tokens) {
		this.tokens = tokens;
	}

	String getQuery() {
		return query;
	}

	void setQuery(String query) {
		this.query = query;
	}

	List<Entity> getEntities() {
		return entities;
	}

	void setEntities(List<Entity> entities) {
		this.entities = entities;
	}

	List<Classes> getClasses() {
		return classes;
	}

	void setClasses(List<Classes> classes) {
		this.classes = classes;
	}

	List<Resource> getResources() {
		return resources;
	}

	void setResources(List<Resource> resources) {
		this.resources = resources;
	}

	boolean isUnion() {
		return isUnion;
	}

	void setUnion(boolean isUnion) {
		this.isUnion = isUnion;
	}

	String getSequence() {
		return sequence;
	}

	void setSequence(String sequence) {
		this.sequence = sequence;
	}

	String getOptions() {
		return options;
	}

	void setOptions(String options) {
		this.options = options;
	}

}