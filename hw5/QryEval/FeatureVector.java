import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;

public class FeatureVector {

	int FEATURENUM = 18;
	ArrayList<Double> featureList;
	HashMap<String, Double> pageRankHash;
	boolean[] disableArray;
	String[] tokens;
	RetrievalModelLetor r;

	public FeatureVector() {
		featureList = new ArrayList<Double>();
		pageRankHash = new HashMap<String, Double>();
		disableArray = new boolean[FEATURENUM];
		for (int i = 0; i < FEATURENUM; i++) {
			disableArray[i] = true;
		}
	}

	public ArrayList<Double> createFV(int docid) throws Exception {
		ArrayList<Double> result = new ArrayList<Double>();

		Document doc = QryEval.READER.document(docid);
		double defaultScore = Double.NaN;
		boolean isTvExist = true;
		TermVector tvBody = null;
		TermVector tvTitle = null;
		TermVector tvUrl = null;
		TermVector tvInlink = null;
	

		String extId = QryEval.getExternalDocid(docid);
		// f1: spam score for d
		if (disableArray[0]) {
			result.add((double) Integer.parseInt(doc.get("score")));
		} else {
			result.add(defaultScore);
		}

		// f2: url depth for d (number of '/' in the rawUrl field)
		if (disableArray[1]) {
			result.add(getUrlDepth(doc.get("rawUrl")));
			// System.err.println("urlscore" + getUrlDepth(doc.get("rawUrl")));
		} else {
			result.add(defaultScore);
		}

		// f3: FromWikipedia score for d (1 if the rawUrl continas
		// "wikepedia.org", o.w. 0)
		if (disableArray[2]) {
			result.add(getWikiScore(doc.get("rawUrl")));
		} else {
			result.add(defaultScore);
		}

		// f4: PageRank score for d
		if (disableArray[3] && pageRankHash.containsKey(extId)) {
			result.add(pageRankHash.get(extId));
		} else {
			result.add(defaultScore);
		}
		
		
		

		try {
			tvBody = new TermVector(docid, "body");
		} catch (Exception e) {
			isTvExist = false;
		}

		HashMap<String, Integer> termHash = new HashMap<String, Integer>();
		if (isTvExist) {
			for (int i = 1; i < tvBody.stemsLength(); i++) {
				String term = tvBody.stemString(i);
				if (!termHash.containsKey(term))
					termHash.put(term, i);
			}
		}

		// f5: BM25 score for <q, d-body>
		if (disableArray[4] && isTvExist) {
			//double test = calBM25Score(docid, "body", tv);
			//System.err.println(test);
			result.add(calBM25Score(docid, "body", tvBody));
		} else {
			result.add(defaultScore);
		}

		// f6: Indri score for <q, d-body>
		if (disableArray[5] && isTvExist) {
			result.add(calIndriScore(docid, "body", tvBody, termHash));
		} else {
			result.add(defaultScore);
		}

		// f7: Term overlap score for <q, d-body>
		if (disableArray[6] && isTvExist) {
			result.add(calOverlapScore(tokens, tvBody, termHash));
		} else {
			result.add(defaultScore);
		}

		
		
		isTvExist = true;
		try {
			tvTitle = new TermVector(docid, "title");
		} catch (Exception e) {
			isTvExist = false;
		}

		HashMap<String, Integer> termHashTitle = new HashMap<String, Integer>();
		if (isTvExist) {
			for (int i = 1; i < tvTitle.stemsLength(); i++) {
				String term = tvTitle.stemString(i);
				if (!termHashTitle.containsKey(term))
					termHashTitle.put(term, i);
			}
		}
		// f8: BM25 score for <q, d-titile>
		if (disableArray[7] && isTvExist) {
			result.add(calBM25Score(docid, "title", tvTitle));
		} else {
			result.add(defaultScore);
		}


		// f9: Indri score for <q, d-title>
		if (disableArray[8] && isTvExist) {
			result.add(calIndriScore(docid, "title", tvTitle, termHashTitle));
		} else {
			result.add(defaultScore);
		}

		// f10: Term overlap score for <q, d-title>
		if (disableArray[9] && isTvExist) {
			result.add(calOverlapScore(tokens, tvTitle, termHashTitle));
		} else {
			result.add(defaultScore);
		}
		
		
		isTvExist = true;
		try {
			tvUrl = new TermVector(docid, "url");
		} catch (Exception e) {
			isTvExist = false;
		}

		HashMap<String, Integer> termHashUrl = new HashMap<String, Integer>();
		if (isTvExist) {
			for (int i = 1; i < tvUrl.stemsLength(); i++) {
				String term = tvUrl.stemString(i);
				if (!termHashUrl.containsKey(term))
					termHashUrl.put(term, i);
			}
		}		

		// f11: BM25 score for <q, d-url>
		if (disableArray[10] && isTvExist) {
			result.add(calBM25Score(docid, "url", tvUrl));
		} else {
			result.add(defaultScore);
		}

		// f12: Indri score for <q, d-url>
		if (disableArray[11] && isTvExist) {
			result.add(calIndriScore(docid, "url", tvUrl, termHashUrl));
		} else {
			result.add(defaultScore);
		}

		// f13: Term overlap score for <q, d-url>
		if (disableArray[12] && isTvExist) {
			result.add(calOverlapScore(tokens, tvUrl, termHashUrl));
		} else {
			result.add(defaultScore);
		}
		
		
		
		
		isTvExist = true;
		try {
			tvInlink = new TermVector(docid, "inlink");
		} catch (Exception e) {
			isTvExist = false;
		}

		HashMap<String, Integer> termHashInlink = new HashMap<String, Integer>();
		if (isTvExist) {
			for (int i = 1; i < tvInlink.stemsLength(); i++) {
				String term = tvInlink.stemString(i);
				if (!termHashInlink.containsKey(term))
					termHashInlink.put(term, i);
			}
		}	

		// f14: BM25 score for <q, d-inlink>
		if (disableArray[13] && isTvExist) {
			result.add(calBM25Score(docid, "inlink", tvInlink));
		} else {
			result.add(defaultScore);
		}


		// f15: Indri score for <q, d-inlink>
		if (disableArray[14] && isTvExist) {
			result.add(calIndriScore(docid, "inlink", tvInlink, termHashInlink));
		} else {
			result.add(defaultScore);
		}

		// f16: Term overlap score for <q, d-inlink>
		if (disableArray[15] && isTvExist) {
			result.add(calOverlapScore(tokens, tvInlink, termHashInlink));
		} else {
			result.add(defaultScore);
		}

		
		isTvExist = true;
		try {
			tvBody = new TermVector(docid, "body");
		} catch (Exception e) {
			isTvExist = false;
		}
		
		// f17: tf*idf
		if (disableArray[16] && isTvExist) {
			result.add(calTfIdf(docid, "body", tvBody, termHash));
		} else {
			result.add(defaultScore);
		}
		
		// f18: docLen normalized score
		if (disableArray[17] && isTvExist) {
			result.add(calDocLenNorm(docid, "body", tvBody, termHash));
		} else {
			result.add(defaultScore);
		}
		return result;
		
	}

	private Double calDocLenNorm(int docid, String field, TermVector tv, HashMap<String, Integer> termHash) throws IOException {
		double score = 0.0;
		double denominator = 1.0;
		double numerator = 0.0;

		for (int i = 0; i < tokens.length; i++) {
			String queryTerm = tokens[i];
			int tf = 0;
			double doclen = QryEval.dls.getDocLength(field, docid);
			
			if (termHash.containsKey(queryTerm) && tv.stemDf(termHash.get(queryTerm)) != 0) {
				int index = termHash.get(queryTerm);
				tf = tv.stemFreq(index);
				numerator += Math.log(tf) + 1;
				denominator += Math.log(doclen) + 1;
				
				
			}		
			
			score = numerator / denominator;
						
		}
	
		return score;
	}
	
	/*
	private Double calDocQuerySim(int docid, TermVector tv, HashMap<String, Integer> termHash) throws IOException {
		double score = 0.0;
		double denominator = 1.0;
		double numerator = 0.0;
		
		
		for (int i = 0; i < tokens.length; i++) {
			String queryTerm = tokens[i];
			int tf = 0;
			if (termHash.containsKey(queryTerm) && tv.stemDf(termHash.get(queryTerm)) != 0) {
				int index = termHash.get(queryTerm);
				tf = tv.stemFreq(index);
				denominator += Math.pow(Math.log(tf) + 1, 2);
				numerator += Math.log(tf) + 1;
				
			}		
			denominator = Math.sqrt(denominator);
			score = numerator / denominator / Math.sqrt(1.0 / tokens.length);
			
			
		}
		
		return score;
	}
	*/
	
	private Double calTfIdf(int docid, String field, TermVector tv, HashMap<String, Integer> termHash) throws IOException {
		// TODO Auto-generated method stub

		double score = 0.0;
		double docNum = (double)(QryEval.READER.numDocs());
		
		for (int i = 0; i < tokens.length; i++) {
			String queryTerm = tokens[i];
			
			if (termHash.containsKey(queryTerm) && tv.stemDf(termHash.get(queryTerm)) != 0) {
				int index = termHash.get(queryTerm);
				int tf = tv.stemFreq(index);
				double df = (double)tv.stemDf(index);
				double idf = Math.log((docNum + 1) / df);
				score += tf * idf;
			}		
		}
		return score;
	}

	private Double calIndriScore(int docid, String field, TermVector tv,
			HashMap<String, Integer> termHash) throws IOException {
		// TODO Auto-generated method stub
		double score = 1.0;
		double corpusLen = QryEval.READER.getSumTotalTermFreq(field);
		double doclen = QryEval.dls.getDocLength(field, docid);
		boolean contain = false;
		double mu = r.getParameter("mu");
		double lambda = r.getParameter("lambda");

		for (int i = 0; i < tokens.length; i++) {
			String queryTerm = tokens[i];
			long ctf = QryEval.READER.totalTermFreq(new Term(field,
					new BytesRef(queryTerm)));
			double mle = (double) ctf / (double) corpusLen;
			int tf = 0;
			if (termHash.containsKey(queryTerm)) {
				tf = tv.stemFreq(termHash.get(queryTerm));
				contain = true;
			}
			double scoreForToken = (1 - lambda) * ((double) tf + mu * mle)
					/ (doclen + mu) + lambda * mle;
			score *= Math.pow(scoreForToken, 1 / (double) tokens.length);
		}

		if (!contain)
			return 0.0;
		return score;
	}

	private Double calOverlapScore(String[] tokens, TermVector tv,
			HashMap<String, Integer> termHash) {
		// TODO Auto-generated method stub
		double score = 0.0;
		double count = 0.0;

		for (int i = 0; i < tokens.length; i++) {
			if (termHash.containsKey(tokens[i]))
				count += 1.0;
		}
		score = count / tokens.length;
		return score;
	}

	private Double calBM25Score(int docid, String field, TermVector tv)
			throws IOException {
		// TODO Auto-generated method stub
		double score = 0.0;
		double k1, b, k3;
		double avgDoclen = (double) QryEval.READER.getSumTotalTermFreq(field)
				/ (double) QryEval.READER.getDocCount(field);
		int docNum = QryEval.READER.numDocs();
		long doclen = QryEval.dls.getDocLength(field, docid);

		k1 = r.getParameter("k_1");
		b = r.getParameter("b");
		k3 = r.getParameter("k_3");

		// read tokens from String array to a HashMap
		HashMap<String, Integer> tokenHash = new HashMap<String, Integer>();
		for (int i = 0; i < tokens.length; i++) {
			tokenHash.put(tokens[i], 0);
		}

		for (int i = 1; i < tv.stemsLength(); i++) {
			// If the word exists in both the document and the query, add score
			if (tokenHash.containsKey(tv.stemString(i))) {
				int df = tv.stemDf(i);
				int tf = tv.stemFreq(i);
				double idf = Math.log((double) (docNum - df + 0.5)
						/ (double) (df + 0.5));
				double tfWeight = tf
						/ ((double) tf + k1
								* ((1 - b) + b * doclen / avgDoclen));
				// userWeight = (k3+1) * qtf / (k3+qtf) = 1;
				score += idf * tfWeight;
			}
		}

		return score;
	}

	private Double getUrlDepth(String url) {
		// TODO Auto-generated method stub
		if (url == null)
			return 0.0;

		double result = 0.0;
		for (int i = 0; i < url.length(); i++) {
			if (url.charAt(i) == '/')
				result += 1.0;
		}
		return result;

	}

	private Double getWikiScore(String url) {
		if (url == null)
			return 0.0;

		if (url.toLowerCase().contains("wikipedia.org"))
			return 1.0;
		return 0.0;

	}
	
	
	public ArrayList<String> normalizedFv(ArrayList<ArrayList<Double>> fvList)
			throws Exception{

		ArrayList<String> result = new ArrayList<String>();
		for (int i = 0; i < FEATURENUM; i++) {
			normSingleFv(fvList, i);
		}
		
		for (int i = 0; i < fvList.size(); i++) {
			StringBuilder strBuilder = new StringBuilder();
			for (int j = 0; j < FEATURENUM; j++) {
				strBuilder.append(j+1).append(":").
					append(fvList.get(i).get(j)).append(" ");
			}
			result.add(strBuilder.toString());
		}
		return result;
	}

	private void normSingleFv(ArrayList<ArrayList<Double>> fvList, int index) {

		if (disableArray[index]) {
			double max = Double.MIN_VALUE;
			double min = Double.MAX_VALUE;
			
			// Get max and min
			for (int i = 0; i < fvList.size(); i++) {
				if (!Double.isNaN((fvList.get(i).get(index)))) {
					max = Math.max(fvList.get(i).get(index), max);
					min = Math.min(fvList.get(i).get(index), min);
				}
			}
			
			if (max == min) {
				for (int i = 0; i < fvList.size(); i++)
					fvList.get(i).set(index, 0.0);
			} else {
				for (int i = 0; i < fvList.size(); i++) {
					double score = fvList.get(i).get(index);
					if (!Double.isNaN(score)) {
						double normScore = (score - min) / (max - min);
						fvList.get(i).set(index, normScore);
					}
					else
						fvList.get(i).set(index, 0.0);
				}
			}
		}
		else {
			for (int i = 0; i < fvList.size(); i++) {
				fvList.get(i).set(index, 0.0);
			}
		}
		
	}

}
