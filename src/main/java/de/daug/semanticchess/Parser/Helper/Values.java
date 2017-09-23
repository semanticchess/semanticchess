package de.daug.semanticchess.Parser.Helper;

import java.util.ArrayList;

public class Values {


	private String valueVars = "";


	private ArrayList<ArrayList<String>> results = new ArrayList<ArrayList<String>>();
	private static ArrayList<String> permutation = new ArrayList<String>();
	
	public Values(){
//		int i = 0;
//		while(i < number){
//			this.valueVars += "?value" + (i+1) + " ";
//			i++;
//		}
//		
	}
	
	public String setValueVarsStr(String valueVar){
		return this.valueVars += valueVar + " ";
	}
	
	public void addResult(ArrayList<String> result){
		this.results.add(result);
	}
	
	//https://stackoverflow.com/a/17193002
	public void generatePermutations(ArrayList<ArrayList<String>> Lists, ArrayList<String> result, int depth, String current)
	{
	    if(depth == Lists.size())
	    {
	       result.add(current);
	       Values.permutation = result;
	       return;
	     }

	    for(int i = 0; i < Lists.get(depth).size(); ++i)
	    {
	        generatePermutations(Lists, result, depth + 1, current + Lists.get(depth).get(i));
	    }
	}
	
	@Override
	public String toString(){
		if(this.getValueVars().equals("")){
			return "";
		}
		
		String values = "VALUES ("+ this.getValueVars() + ") {";
		for (String p : permutation){
			values += "(" + p + ")";
		}
		values += "}";
		
		return values;
	}
	
	
	

	public String getValueVars() {
		return valueVars;
	}

	public void setValueVars(String valueVars) {
		this.valueVars += valueVars + " ";
	}

	public ArrayList<ArrayList<String>> getResults() {
		return results;
	}

	public void setResults(ArrayList<ArrayList<String>> results) {
		this.results = results;
	}
	

	public static ArrayList<String> getPermutation() {
		return permutation;
	}

	public static void setPermutation(ArrayList<String> result) {
		Values.permutation = result;
	}

	public static void main (String[] args){
		Values v = new Values();

		ArrayList<String> a = new ArrayList<String>();
		ArrayList<String> b = new ArrayList<String>();
		ArrayList<String> c = new ArrayList<String>();
		ArrayList<String> d = new ArrayList<String>();
		
		a.add("'Wilhelm Steinitz'");
		a.add("'V Steinitz'");
		
		b.add("'London Eng'");
		
		c.add("'Wilhelm Steinitz'");
		c.add("'V Steinitz'");
		c.add("'Emanuel Lasker'");
		
		d.add("'London Eng'");
		
		v.addResult(a);
		v.addResult(b);
		v.addResult(c);
		v.addResult(d);
		//v.setResultStr();
		
		String tempStr = "";
		v.generatePermutations(v.results, permutation, 0, tempStr);
		System.out.println(v.toString());
		//System.out.println(v.getResultsStr());
	}
	
}