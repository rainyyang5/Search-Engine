/**
 *  QryEval illustrates the architecture for the portion of a search
 *  engine that evaluates queries.  It is a template for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class QryEval {
	static String usage = "Usage:  java "
			+ System.getProperty("sun.java.command") + " paramFile\n\n";

	// The index file reader is accessible via a global variable. This
	// isn't great programming style, but the alternative is for every
	// query operator to store or pass this value, which creates its
	// own headaches.

	public static IndexReader READER;
	public static DocLengthStore dls;
	public static boolean isBM25 = false;
	public static boolean isIndri = false;

	// Create and configure an English analyzer that will be used for
	// query parsing.

	public static EnglishAnalyzerConfigurable analyzer = new EnglishAnalyzerConfigurable(
			Version.LUCENE_43);
	static {
		analyzer.setLowercase(true);
		analyzer.setStopwordRemoval(true);
		analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
	}

	/**
	 * @param args
	 *            The only argument is the path to the parameter file.
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		// must supply parameter file
		if (args.length < 1) {
			System.err.println(usage);
			System.exit(1);
		}

		// read in the parameter file; one parameter per line in format of
		// key=value
		Map<String, String> params = new HashMap<String, String>();
		Scanner scan = new Scanner(new File(args[0]));
		String line = null;
		do {
			line = scan.nextLine();
			String[] pair = line.split("=");
			params.put(pair[0].trim(), pair[1].trim());
		} while (scan.hasNext());
		scan.close();

		// parameters required for this example to run
		if (!params.containsKey("indexPath")) {
			System.err.println("Error: Parameters were missing.");
			System.exit(1);
		}

		// open the index
		READER = DirectoryReader.open(FSDirectory.open(new File(params
				.get("indexPath"))));

		if (READER == null) {
			System.err.println(usage);
			System.exit(1);
		}

		dls = new DocLengthStore(READER);
		// RetrievalModel model = new RetrievalModelUnrankedBoolean();

		RetrievalModel model = null;

		if (params.get("retrievalAlgorithm").equals("UnrankedBoolean"))
			model = new RetrievalModelUnrankedBoolean();
		else if (params.get("retrievalAlgorithm").equals("RankedBoolean"))
			model = new RetrievalModelRankedBoolean();
		else if (params.get("retrievalAlgorithm").equals("BM25")) {
			isBM25 = true;
			String k1 = params.get("BM25:k_1");
			String k3 = params.get("BM25:k_3");
			String b = params.get("BM25:b");
			if (k1 == null || k3 == null || b == null)
				System.out
						.println("Error: please input 3 parameters for BM25.");
			else if (Double.parseDouble(k1) < 0)
				System.out
						.println("Error: plaese input a positive number for k1.");
			else if (Double.parseDouble(k3) < 0)
				System.out
						.println("Error: plaese input a positive number for k3.");
			else if (Double.parseDouble(b) < 0 || Double.parseDouble(b) > 1.0)
				System.out
						.println("Error: parameter b should be in the range 0.0-1.0.");
			else {
				double doublek1 = Double.parseDouble(k1);
				double doublek3 = Double.parseDouble(k3);
				double doubleb = Double.parseDouble(b);
				model = new RetrievalModelBM25(doublek1, doubleb, doublek3);
			}
		}

		else if (params.get("retrievalAlgorithm").equals("Indri")) {
			isIndri = true;
			String mu = params.get("Indri:mu");
			String lambda = params.get("Indri:lambda");

			if (mu == null || lambda == null)
				System.out
						.println("Error: please input 2 parameters for Indri.");
			else if (Double.parseDouble(mu) < 0)
				System.out
						.println("Error: plaese input a positive number for mu.");
			else if (Double.parseDouble(lambda) < 0
					|| Double.parseDouble(lambda) > 1.0)
				System.out
						.println("Error: parameter lambda should be in the range 0.0-1.0.");
			else {
				int intmu = Integer.parseInt(mu);
				double doublelambda = Double.parseDouble(lambda);
				model = new RetrievalModelIndri(intmu, doublelambda);
			}
		}

		boolean fb = false;
		boolean fbInitial = false;
		String fbInitialRankingFile = null;
		String fbExpansionQueryFile = null;
		Double fbOrigWeight = null;
		Integer fbMu = null;
		Integer fbTerms = null;
		Integer fbDocs = null;

		if (params.get("fb") != null) {
			fb = Boolean.parseBoolean(params.get("fb"));
		}

		if (fb) {

			if (params.containsKey("fbInitialRankingFile")) {
				fbInitialRankingFile = params.get("fbInitialRankingFile");
				fbInitial = true;
			}

			if (params.containsKey("fbExpansionQueryFile"))
				fbExpansionQueryFile = params.get("fbExpansionQueryFile");

			fbOrigWeight = Double.valueOf(params.get("fbOrigWeight"));
			if (fbOrigWeight < 0.0 || fbOrigWeight > 1.0) {
				System.out
						.println("Error: fbOrigWeight should be between 0.0 and 1.0. Given value is:"
								+ fbOrigWeight);
			}

			fbMu = Integer.valueOf(params.get("fbMu"));
			if (fbMu < 0) {
				System.out
						.println("Error: fbMu should be greater than or equal to 0. Given value is:"
								+ fbMu);
			}

			fbTerms = Integer.valueOf(params.get("fbTerms"));
			if (fbTerms <= 0) {
				System.out
						.println("Error: fbTerms should be greater than 0. Given value is:"
								+ fbTerms);
			}

			fbDocs = Integer.valueOf(params.get("fbDocs"));
			if (fbDocs <= 0) {
				System.out
						.println("Error: fbDocs should be greater than 0. Given value is:"
								+ fbDocs);
			}

		}

		StringBuffer printStr = new StringBuffer();
		List<QryResult> resultList = new LinkedList<QryResult>();

		// Initial QryResult given fbInitialRankingFile
		if (fb && fbInitial) {

			QryResult result = new QryResult();

			FileReader fileReader = new FileReader(fbInitialRankingFile);
			BufferedReader bufferReader = new BufferedReader(fileReader);
			String extID;
			int intID;
			double score;
			String curLine;

			String queryID;
			String previousQueryID = null;

			while ((curLine = bufferReader.readLine()) != null) {
				// System.err.println(curLine);
				String field[] = curLine.trim().split(" ");
				queryID = field[0];
				extID = field[2];
				score = Double.parseDouble(field[4]);
				intID = getInternalDocid(extID);

				if (previousQueryID == null)
					previousQueryID = queryID;

				if (queryID.equals(previousQueryID)) {
					result.docScores.add(intID, score);
				} else { // queryID != previoiusQueryID

					resultList.add(result);
					result = new QryResult();
					previousQueryID = queryID;
					result.docScores.add(intID, score);

				}
			}
			resultList.add(result);
			bufferReader.close();

		}

		FileReader fileReader = new FileReader(params.get("queryFilePath"));
		BufferedReader queryReader = new BufferedReader(fileReader);

		Qryop queryTree;
		String curLine;
		String queryID;
		String queryContent;
		StringBuffer expansionOutputBuffer = new StringBuffer();

		int index = 0;
		QryResult fbInitialResult;
		while ((curLine = queryReader.readLine()) != null) {
			int colonIndex = curLine.indexOf(':');
			queryID = curLine.substring(0, colonIndex);
			String content = curLine.substring(colonIndex + 1);

			// Add a default operator to avoid unprocessed token
			if (isBM25) {
				queryContent = "#SUM(" + content + ")";
			} else if (isIndri)
				queryContent = "#AND(" + content + ")";
			else
				queryContent = "#OR(" + content + ")";

			queryTree = parseQuery(queryContent);

			// QryopSlScore opScore = new QryopSlScore(queryTree);

			StringBuffer strBuffer = new StringBuffer();
			QryResult result = queryTree.evaluate(model);

			if (fb && fbInitial) {
				fbInitialResult = resultList.get(index);
				index++;
				QryResult fbResult = expansion(fbInitialResult, content, model,
						queryID, fbExpansionQueryFile, fbDocs, fbTerms, fbMu,
						fbOrigWeight, expansionOutputBuffer);
				strBuffer = printResults(queryID, fbResult);
			}

			else if (fb) {
				QryResult fbResult = expansion(result, content, model, queryID,
						fbExpansionQueryFile, fbDocs, fbTerms, fbMu,
						fbOrigWeight, expansionOutputBuffer);
				strBuffer = printResults(queryID, fbResult);
			} else
				strBuffer = printResults(queryID, result);

			System.out.println(strBuffer);
			printStr.append(strBuffer);
		}

		queryReader.close();

		/* write the output to the file given the output path */
		BufferedWriter bufferWriter = null;

		try {
			File file = new File(params.get("trecEvalOutputPath"));
			FileWriter fileWriter = new FileWriter(file);
			bufferWriter = new BufferedWriter(fileWriter);
			bufferWriter.write(printStr.toString());
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				bufferWriter.close();
			} catch (Exception e) {
			}
		}
		
		/* write the expansion query to fbExpansionQueryFile */
		BufferedWriter expansionWriter = null;
		if (fbExpansionQueryFile != null) {

			try {
				File file = new File(fbExpansionQueryFile);
				FileWriter fileWriter = new FileWriter(file);
				expansionWriter = new BufferedWriter(fileWriter);
				expansionWriter.write(expansionOutputBuffer.toString());
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					expansionWriter.close();
				} catch (Exception e) {
				}
			}
		}

		

		/*
		 * The code below is an unorganized set of examples that show you
		 * different ways of accessing the index. Some of these are only useful
		 * in HW2 or HW3.
		 */

		// Lookup the document length of the body field of doc 0.
		// System.out.println(s.getDocLength("body", 0));

		// How to use the term vector.
		/*
		 * TermVector tv = new TermVector(1, "body");
		 * System.out.println(tv.stemString(10)); // get the string for the 10th
		 * stem System.out.println(tv.stemDf(10)); // get its df
		 * System.out.println(tv.totalStemFreq(10)); // get its ctf
		 */

		/**
		 * The index is open. Start evaluating queries. The examples below show
		 * query trees for two simple queries. These are meant to illustrate how
		 * query nodes are created and connected. However your software will not
		 * create queries like this. Your software will use a query parser. See
		 * parseQuery.
		 *
		 * The general pattern is to tokenize the query term (so that it gets
		 * converted to lowercase, stopped, stemmed, etc), create a Term node to
		 * fetch the inverted list, create a Score node to convert an inverted
		 * list to a score list, evaluate the query, and print results.
		 * 
		 * Modify the software so that you read a query from a file, parse it,
		 * and form the query tree automatically.
		 */

		// A one-word query.
		/*
		 * StringBuffer buffer = printResults("pea", (new QryopSlScore( new
		 * QryopIlTerm(tokenizeQuery("pea")[0]))).evaluate(model));
		 * System.out.println (buffer.toString());
		 */
		// A more complex query.
		/*
		 * printResults("#AND (aparagus broccoli cauliflower #SYN(peapods peas))"
		 * , (new QryopSlAnd( new QryopIlTerm(tokenizeQuery("asparagus")[0]),
		 * new QryopIlTerm(tokenizeQuery("broccoli")[0]), new
		 * QryopIlTerm(tokenizeQuery("cauliflower")[0]), new QryopIlSyn( new
		 * QryopIlTerm(tokenizeQuery("peapods")[0]), new
		 * QryopIlTerm(tokenizeQuery("peas")[0])))).evaluate(model));
		 */
		// A different way to create the previous query. This doesn't use
		// a stack, but it may make it easier to see how you would parse a
		// query with a stack-based architecture.
		/*
		 * Qryop op1 = new QryopSlAnd(); op1.add (new
		 * QryopIlTerm(tokenizeQuery("asparagus")[0])); op1.add (new
		 * QryopIlTerm(tokenizeQuery("broccoli")[0])); op1.add (new
		 * QryopIlTerm(tokenizeQuery("cauliflower")[0])); Qryop op2 = new
		 * QryopIlSyn(); op2.add (new QryopIlTerm(tokenizeQuery("peapods")[0]));
		 * op2.add (new QryopIlTerm(tokenizeQuery("peas")[0])); op1.add (op2);
		 * printResults
		 * ("#AND (aparagus broccoli cauliflower #SYN(peapods peas))",
		 * op1.evaluate(model));
		 */
		// Using the example query parser. Notice that this does no
		// lexical processing of query terms. Add that to the query
		// parser.
		/*
		 * Qryop qTree; String query = new String ("#AND(apple pie)"); qTree =
		 * parseQuery (query); printResults (query, qTree.evaluate (model));
		 */
		/*
		 * Create the trec_eval output. Your code should write to the file
		 * specified in the parameter file, and it should write the results that
		 * you retrieved above. This code just allows the testing infrastructure
		 * to work on QryEval.
		 */
		/*
		 * BufferedWriter writer = null;
		 * 
		 * try { writer = new BufferedWriter(new FileWriter(new
		 * File("teval.in")));
		 * 
		 * writer.write("1 Q0 clueweb09-enwp01-75-20596 1 1.0 run-1");
		 * writer.write("1 Q0 clueweb09-enwp01-58-04573 2 0.9 run-1");
		 * writer.write("1 Q0 clueweb09-enwp01-24-11888 3 0.8 run-1");
		 * writer.write("2 Q0 clueweb09-enwp00-70-20490 1 0.9 run-1"); } catch
		 * (Exception e) { e.printStackTrace(); } finally { try {
		 * writer.close(); } catch (Exception e) { } }
		 */
		// Later HW assignments will use more RAM, so you want to be aware
		// of how much memory your program uses.

		printMemoryUsage(false);

	}

	
	static QryResult expansion(QryResult result, String origQuery,
			RetrievalModel model, String queryID, String fbExpansionQueryFile,
			int fbDocs, int fbTerms, int fbMu, double fbOrigWeight, StringBuffer expansionOutputBuffer)
			throws Exception {

		HashMap<String, Double> termScoreHM = new HashMap<String, Double>();
		QryResult fbResult = new QryResult();
		HashMap<Integer, String> extIDHM = null;
		ExtDocId extDocId;
		int scoreListSize = result.docScores.scores.size();

		/*
		 * If the original query does not have any matched documents, return
		 * empty result and write the original query to fbExpansionQueryFile
		 */
		if (result == null || scoreListSize < 1) {
			expansionOutputBuffer.append(origQuery).append("\n");
			//writeExpandedQuery(origQuery, fbExpansionQueryFile);
			return fbResult;
		}

		/*
		 * put all the results of the original query into HashMap and sort by
		 * score
		 */
		extDocId = new ExtDocId(result);
		extDocId.store();
		extIDHM = extDocId.getHM();
		result.sortResult(extIDHM);

		double ctotallen = READER.getSumTotalTermFreq("body");

		/* put all the term into term-score HashMap */
		for (int i = 0; (i < fbDocs) && (i < scoreListSize); i++) {

			String externalId = extIDHM.get(result.docScores.getDocid(i));
			int docid = getInternalDocid(externalId);			
			TermVector tv = new TermVector(docid, "body");
			int stemlen = tv.stemsLength();
			

			for (int j = 1; j < stemlen; j++) {
				String term = tv.stemString(j);
				if (term.contains(".") || term.contains(",")) {
					continue;
				}

				if (!termScoreHM.containsKey(term))
					termScoreHM.put(term, 0.0);
			}
		}

		/*
		 * Initial flagHM, set false to a term when the default scores is not
		 * added
		 */
		HashMap<String, Boolean> flagHM = new HashMap<String, Boolean>();
		List<Map.Entry<String, Double>> listHM = new LinkedList<Map.Entry<String, Double>>(
				termScoreHM.entrySet());
		int listSize = listHM.size();

		for (int index = 0; index < listSize; index++) {
			String term = listHM.get(index).getKey();
			flagHM.put(term, false);
		}
		
		/* get all the doclen and store them into a list so that we can use it to calculate 
		 * default scores later
		 */
		int defaultTimes = Math.min(fbDocs, scoreListSize);
		List<Long> doclenList = new LinkedList<Long>();
		List<Double> docScoreList = new LinkedList<Double>();
		
		for (int i = 0; i < defaultTimes; i++) {
			String externalId = extIDHM.get(result.docScores.getDocid(i));
			int docid = getInternalDocid(externalId);
			long doclen = dls.getDocLength("body", docid);
			double docScore = result.docScores.getDocidScore(i);
			doclenList.add(doclen);
			docScoreList.add(docScore);
		}
		
		
		/*
		 * update score for each term. Added all the default scores when a
		 * document contains a term and then set the flagHM to be true
		 */
		for (int i = 0; i < defaultTimes; i++) {

			String externalId = extIDHM.get(result.docScores.getDocid(i));
			int docid = getInternalDocid(externalId);
			double docScore = result.docScores.getDocidScore(i);//?

			TermVector tv = new TermVector(docid, "body");
			int stemlen = tv.stemsLength();

			long doclen = dls.getDocLength("body", docid);

			for (int j = 1; j < stemlen; j++) {
				String term = tv.stemString(j);
				if (term.contains(".") || term.contains(",")) {
					continue;
				}
				int tf = tv.stemFreq(j);
				long ctf = tv.totalStemFreq(j);

				double ptc = (double) ctf / (double) ctotallen;
				double ptd_contain = ((double) tf)
						/ (double) ((double) doclen + fbMu);
				
				double idf = (double) (Math.log(1 / ptc));

				double termScore = ptd_contain * docScore * idf;				
				double termScoreInHash = termScoreHM.get(term);
				termScoreInHash = termScoreInHash + termScore;

				if (flagHM.get(term) == false) {

					double ptd_default;
					double defaultScore = 0.0;
					long doclen_k;
					double docScore_k;
					for (int k = 0; k < defaultTimes; k++) {
						doclen_k = doclenList.get(k);
						docScore_k = docScoreList.get(k);
						ptd_default = ((double)fbMu * ptc)
								/ ((double)doclen_k + (double)fbMu);
						defaultScore += ptd_default * docScore_k * idf;
					}

					termScoreInHash += defaultScore;
					flagHM.put(term, true);
				}
				termScoreHM.put(term, termScoreInHash);

			}

		}

		/*
		 * Sort termScoreHM and get the first fbTerms term to form the learned
		 * Query
		 */
		List<Map.Entry<String, Double>> list = new LinkedList<Map.Entry<String, Double>>(
				termScoreHM.entrySet());

		Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
			public int compare(Map.Entry<String, Double> o1,
					Map.Entry<String, Double> o2) {
				return (o2.getValue()).compareTo(o1.getValue());
			}
		});

		StringBuffer learnedQuery = new StringBuffer();
		learnedQuery.append("#WAND (");
		for (int i = (fbTerms - 1); i >= 0; i--) {
			String term = list.get(i).getKey();
			double score = list.get(i).getValue();
			String formatScore = String.format("%.4f", score);
			learnedQuery.append(formatScore).append(" ").append(term)
					.append(" ");
		}
		learnedQuery.append(")");

		/* form the new query and generate results */
		double learnedWeight = 1 - (double) fbOrigWeight;
		String expandedQuery = "#WAND ( " + fbOrigWeight + " #AND( "
				+ origQuery + " ) " + learnedWeight + " " + learnedQuery + ")";

		// Add a default operator to avoid unprocessed token
		if (isBM25) {
			expandedQuery = "#SUM(" + expandedQuery + ")";
		} else if (isIndri)
			expandedQuery = "#AND(" + expandedQuery + ")";
		else
			expandedQuery = "#OR(" + expandedQuery + ")";

		Qryop expandedQueryTree = parseQuery(expandedQuery);
		fbResult = expandedQueryTree.evaluate(model);

		/* write the expanded query to output file */
		String expansionOutput = queryID + ": " + learnedQuery;
		expansionOutputBuffer.append(expansionOutput).append("\n");
		
		//writeExpandedQuery(expansionOutput, fbExpansionQueryFile);

		return fbResult;
	}

	/*
	static void writeExpandedQuery(String expansionOutput,
			String fbExpansionQueryFile) {

		BufferedWriter bufferWriter = null;
		if (fbExpansionQueryFile != null) {

			try {
				File file = new File(fbExpansionQueryFile);
				FileWriter fileWriter = new FileWriter(file);
				bufferWriter = new BufferedWriter(fileWriter);
				bufferWriter.write(expansionOutput.toString());
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					//bufferWriter.close();
				} catch (Exception e) {
				}
			}
		}

	}*/

	/**
	 * Write an error message and exit. This can be done in other ways, but I
	 * wanted something that takes just one statement so that it is easy to
	 * insert checks without cluttering the code.
	 * 
	 * @param message
	 *            The error message to write before exiting.
	 * @return void
	 */
	static void fatalError(String message) {
		System.err.println(message);
		System.exit(1);
	}

	/**
	 * Get the external document id for a document specified by an internal
	 * document id. If the internal id doesn't exists, returns null.
	 * 
	 * @param iid
	 *            The internal document id of the document.
	 * @throws IOException
	 */
	static String getExternalDocid(int iid) throws IOException {
		Document d = QryEval.READER.document(iid);
		String eid = d.get("externalId");
		return eid;
	}

	/**
	 * Finds the internal document id for a document specified by its external
	 * id, e.g. clueweb09-enwp00-88-09710. If no such document exists, it throws
	 * an exception.
	 * 
	 * @param externalId
	 *            The external document id of a document.s
	 * @return An internal doc id suitable for finding document vectors etc.
	 * @throws Exception
	 */
	static int getInternalDocid(String externalId) throws Exception {
		Query q = new TermQuery(new Term("externalId", externalId));

		IndexSearcher searcher = new IndexSearcher(QryEval.READER);
		TopScoreDocCollector collector = TopScoreDocCollector.create(1, false);
		searcher.search(q, collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs;

		if (hits.length < 1) {
			throw new Exception("External id not found.");
		} else {
			return hits[0].doc;
		}
	}

	/**
	 * parseQuery converts a query string into a query tree.
	 * 
	 * @param qString
	 *            A string containing a query.
	 * @param qTree
	 *            A query tree
	 * @throws IOException
	 */
	static Qryop parseQuery(String qString) throws IOException {

		Qryop currentOp = null;
		Stack<Qryop> stack = new Stack<Qryop>();

		// Add a default query operator to an unstructured query. This
		// is a tiny bit easier if unnecessary whitespace is removed.

		qString = qString.trim();

		if (qString.charAt(0) != '#') {
			qString = "#or(" + qString + ")";
		}

		// Tokenize the query.

		StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()",
				true);
		String token = null;

		// Each pass of the loop processes one token. To improve
		// efficiency and clarity, the query operator on the top of the
		// stack is also stored in currentOp.
		double weight = -1.0;
		Stack<Double> weightStack = new Stack<Double>();

		boolean isWeight = true;

		while (tokens.hasMoreTokens()) {

			boolean isWop = false;
			Qryop topOp = null;
			token = tokens.nextToken();

			if (!stack.isEmpty())
				topOp = stack.peek();
			if (topOp instanceof QryopSlWand || topOp instanceof QryopSlWsum)
				isWop = true;
			else
				isWop = false;

			if (token.matches("[ ,(\t\n\r]")) {
				// Ignore most delimiters
				// if it is "(" set isWeight to be true
				if (Pattern.matches("[(]", token))
					isWeight = true;
			} else if (isWop && isWeight && !token.matches("[)]")) {

				// If the top operator is W and the token is a weight,
				// Store the weight and set the boolean to be false, so
				// that the next token is not weight (word)
				// if statement: Ensure that the token is not ")" or other
				// non-weight
				if (Pattern.matches("^[0-9]*\\.?[0-9]*$", token)) {
					weight = Double.parseDouble(token);
					weightStack.push(weight);
					isWeight = false;
				}
			} else if (token.equalsIgnoreCase("#and")) {
				if (isIndri)
					currentOp = new QryopSlIndriAnd();
				else
					currentOp = new QryopSlAnd();
				stack.push(currentOp);
			} else if (token.equalsIgnoreCase("#syn")) {
				currentOp = new QryopIlSyn();
				stack.push(currentOp);
			} else if (token.equalsIgnoreCase("#or")) {
				currentOp = new QryopSlOr();
				stack.push(currentOp);
			} else if (token.equalsIgnoreCase("#score")) {
				currentOp = new QryopSlScore();
				stack.push(currentOp);
			} else if (token.toLowerCase().startsWith("#near")) {
				int divIndex = token.indexOf("/");
				int distance = Integer.parseInt(token.trim().substring(
						divIndex + 1));
				currentOp = new QryopIlNear(distance);
				stack.push(currentOp);
			} else if (token.equalsIgnoreCase("#sum")) {
				currentOp = new QryopSlSum();
				stack.push(currentOp);
			} else if (token.toLowerCase().startsWith("#window")) {
				int divIndex = token.indexOf("/");
				int distance = Integer.parseInt(token.trim().substring(
						divIndex + 1));
				currentOp = new QryopIlWindow(distance);
				stack.push(currentOp);
			} else if (token.equalsIgnoreCase("#WAND")) {
				if (isIndri) {
					currentOp = new QryopSlWand();
				}
				stack.push(currentOp);
			} else if (token.equalsIgnoreCase("#WSUM")) {
				if (isIndri) {
					currentOp = new QryopSlWsum();
				}
				stack.push(currentOp);
			} else if (token.startsWith(")")) { // Finish current query
												// operator.
				// If the current query operator is not an argument to
				// another query operator (i.e., the stack is empty when it
				// is removed), we're done (assuming correct syntax - see
				// below). Otherwise, add the current operator as an
				// argument to the higher-level operator, and shift
				// processing back to the higher-level operator.

				stack.pop();

				// if (weight != -1.0 && braceCnt == 0) {

				// weight = -1.0;
				// }
				if (stack.empty()) {

					break;
				}
				Qryop arg = currentOp;
				// System.err.println(arg);
				currentOp = stack.peek();
				if (currentOp instanceof QryopSlWand
						|| currentOp instanceof QryopSlWsum) {
					if (!weightStack.empty()) {
						weight = weightStack.pop();
					}
					((QryopSlW) currentOp).addWeight(weight);
				}
				currentOp.add(arg);

			} else {
				// System.err.println("enter else");

				// NOTE: You should do lexical processing of the token before
				// creating the query term, and you should check to see whether
				// the token specifies a particular field (e.g., apple.title).
				if (isWop) {
					// To execute this block, if isWop is true, isWeight must be
					// false,
					// Otherwise it will execute the setweight block above
					isWeight = true;
				}
				String[] tokenizedWord = tokenizeQuery(token);

				if (tokenizedWord.length > 0) {

					String field;
					String tokenString = Arrays.toString(tokenizedWord);
					int dotIndex = tokenString.indexOf(".");
					int bracketIndex = tokenString.indexOf("]");

					if (dotIndex != -1) { // has specific field

						String[] word = tokenizeQuery(tokenString.substring(1,
								dotIndex));
						field = tokenString.substring(dotIndex + 1,
								bracketIndex);

						if (word == null || word.length == 0) { // Term is
																// stopword
							if (isWop) {
								weight = weightStack.pop(); // remove the
															// corresponding
															// weight
							}
						} else {
							if (isWop) {
								weight = weightStack.pop();
								((QryopSlW) currentOp).add(new QryopIlTerm(
										word[0], field));
								((QryopSlW) currentOp).addWeight(weight);

							} else {
								currentOp.add(new QryopIlTerm(word[0], field));
							}
						}
					} else { // don't have explicit field

						if (isWop) {
							weight = weightStack.pop();
							((QryopSlW) currentOp).add(new QryopIlTerm(
									tokenizedWord[0]));
							((QryopSlW) currentOp).addWeight(weight);

						} else
							currentOp.add(new QryopIlTerm(tokenizedWord[0]));
					}
				}
			}
		}

		// A broken structured query can leave unprocessed tokens on the
		// stack, so check for that.
		if (tokens.hasMoreTokens()) {
			System.err
					.println("Error:  Query syntax is incorrect.  " + qString);
			return null;
		}

		return currentOp;
	}

	/**
	 * Print a message indicating the amount of memory used. The caller can
	 * indicate whether garbage collection should be performed, which slows the
	 * program but reduces memory usage.
	 * 
	 * @param gc
	 *            If true, run the garbage collector before reporting.
	 * @return void
	 */
	public static void printMemoryUsage(boolean gc) {

		Runtime runtime = Runtime.getRuntime();

		if (gc) {
			runtime.gc();
		}

		System.out
				.println("Memory used:  "
						+ ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L))
						+ " MB");
	}

	/**
	 * Print the query results.
	 * 
	 * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
	 * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
	 * 
	 * QueryID Q0 DocID Rank Score RunID
	 * 
	 * @param queryName
	 *            Original query.
	 * @param result
	 *            Result object generated by {@link Qryop#evaluate()}.
	 * @throws IOException
	 */
	static StringBuffer printResults(String queryID, QryResult result)
			throws IOException {

		StringBuffer buffer = new StringBuffer();
		int scoreListSize = result.docScores.scores.size();
		HashMap<Integer, String> extIDHM = null;
		ExtDocId extDocId;

		if (result != null) {
			extDocId = new ExtDocId(result);
			extDocId.store();
			extIDHM = extDocId.getHM();
			result.sortResult(extIDHM);
		}
		if (result == null || scoreListSize < 1) {
			buffer.append(queryID + " Q0 dummy 1 0.000000000000 run-1");
		} else {
			int cnt = 1;
			for (int i = 0; i < scoreListSize; i++) {

				if (cnt <= 100) {
					buffer.append(queryID).append(" Q0 ")
							.append(extIDHM.get(result.docScores.getDocid(i)))
							.append(" ").append(cnt).append(" ")
							.append(result.docScores.getDocidScore(i))
							.append(" ").append("run-1").append("\n");
					cnt++;
				} else
					return buffer;
			}
		}

		return buffer;
	}

	/**
	 * Given a query string, returns the terms one at a time with stopwords
	 * removed and the terms stemmed using the Krovetz stemmer.
	 * 
	 * Use this method to process raw query terms.
	 * 
	 * @param query
	 *            String containing query
	 * @return Array of query tokens
	 * @throws IOException
	 */
	static String[] tokenizeQuery(String query) throws IOException {

		TokenStreamComponents comp = analyzer.createComponents("dummy",
				new StringReader(query));
		TokenStream tokenStream = comp.getTokenStream();

		CharTermAttribute charTermAttribute = tokenStream
				.addAttribute(CharTermAttribute.class);
		tokenStream.reset();

		List<String> tokens = new ArrayList<String>();
		while (tokenStream.incrementToken()) {
			String term = charTermAttribute.toString();
			tokens.add(term);
		}
		return tokens.toArray(new String[tokens.size()]);
	}
}
