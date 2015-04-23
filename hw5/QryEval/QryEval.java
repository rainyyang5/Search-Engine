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
import java.io.InputStreamReader;
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
		} else if (params.get("retrievalAlgorithm").equals("letor")) {
			String mu = params.get("Indri:mu");
			String lambda = params.get("Indri:lambda");
			String k1 = params.get("BM25:k_1");
			String k3 = params.get("BM25:k_3");
			String b = params.get("BM25:b");

			if (mu == null || lambda == null || k1 == null || k3 == null
					|| b == null)
				System.out
						.println("Error: please input 2 parameters for Indri and 3 parameters for BM25.");
			else if (Double.parseDouble(mu) < 0)
				System.out
						.println("Error: plaese input a positive number for mu.");
			else if (Double.parseDouble(lambda) < 0
					|| Double.parseDouble(lambda) > 1.0)
				System.out
						.println("Error: parameter lambda should be in the range 0.0-1.0.");
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
				int intmu = Integer.parseInt(mu);
				double doublelambda = Double.parseDouble(lambda);
				double doublek1 = Double.parseDouble(k1);
				double doublek3 = Double.parseDouble(k3);
				double doubleb = Double.parseDouble(b);
				model = new RetrievalModelLetor((double) intmu, doublelambda,
						doublek1, doubleb, doublek3);
			}
		} else {
			System.err.println("Error: Retrieval model is not implemented.");
			System.exit(1);
		}

		/*
		 * Letor
		 */
		if (model instanceof RetrievalModelLetor) {

			/*
			 * Calculate feature vector from training query and training
			 * relevance files
			 */
			// Read pageRank into a hashmap (exterDocid, score)
			HashMap<String, Double> pageRankHash = new HashMap<String, Double>();
			BufferedReader pageRankReader = new BufferedReader(new FileReader(
					params.get("letor:pageRankFile")));
			String readline = null;
			while ((readline = pageRankReader.readLine()) != null) {
				String[] field = readline.split("\t");
				pageRankHash.put(field[0], Double.valueOf(field[1]));
			}
			pageRankReader.close();

			// Read disableFile into a boolean array
			boolean disableArray[] = new boolean[18];
			for (int i = 0; i < 18; i++) {
				disableArray[i] = true;
			}

			if (params.containsKey("letor:featureDisable")) {
				String disableStr = params.get("letor:featureDisable");
				String[] fields = disableStr.trim().split(",");
				for (int i = 0; i < fields.length; i++) {

					int index = (Integer.parseInt(fields[i].trim()) - 1);
					disableArray[index] = false;
				}
			}

			// Read relevance file into a hashmap (queryID, Arraylist of
			// relRecords)
			Map<String, ArrayList<String>> relHash = new HashMap<String, ArrayList<String>>();
			BufferedReader relHashReader = new BufferedReader(new FileReader(
					new File(params.get("letor:trainingQrelsFile"))));
			String relLine = null;

			while ((relLine = relHashReader.readLine()) != null) {
				String[] relFields = relLine.split("\\s+");
				String queryID = relFields[0].trim();

				if (relHash.containsKey(queryID))// the query already exists
				{
					ArrayList<String> content = relHash.get(queryID);
					content.add(relLine);
					relHash.put(queryID, content);
				} else {
					ArrayList<String> content = new ArrayList<String>();
					content.add(relLine);
					relHash.put(queryID, content);
				}
			}
			relHashReader.close();

			// train a model
			BufferedReader trainQueryReader = new BufferedReader(
					new FileReader(new File(
							params.get("letor:trainingQueryFile"))));
			String queryLine = null;
			// BufferedWriter FvWriter = null;
			BufferedWriter FvWriter = new BufferedWriter(new FileWriter(
					new File(params.get("letor:trainingFeatureVectorsFile"))));
			while ((queryLine = trainQueryReader.readLine()) != null) {

				String[] field = queryLine.split(":");
				String qid = field[0].trim();
				String[] tokens = tokenizeQuery(field[1]);

				ArrayList<String> relContent = relHash.get(qid);

				// Calculate FV list
				ArrayList<ArrayList<Double>> fvList = new ArrayList<ArrayList<Double>>();
				FeatureVector fv = new FeatureVector();
				fv.r = (RetrievalModelLetor) model;
				fv.tokens = tokens;
				fv.disableArray = disableArray;
				fv.pageRankHash = pageRankHash;

				for (int i = 0; i < relContent.size(); i++) {
					String extId = relContent.get(i).split("\\s+")[2];
					int intId = getInternalDocid(extId);
					fvList.add(fv.createFV(intId));
				}

				ArrayList<String> normalizedFv = fv.normalizedFv(fvList);

				
				// write FVlist into trainingFeatureVectorsFile
				for (int i = 0; i < relContent.size(); i++) {
					String[] relContentField = relContent.get(i).split("\\s+");
					StringBuilder strBuilder = new StringBuilder();
					String extId = relContentField[2];
					String relScore = relContentField[3];
					strBuilder.append(relScore).append(" qid:").append(qid)
							.append(" ").append(normalizedFv.get(i))
							.append("# ").append(extId);
					FvWriter.write(strBuilder.toString());
					FvWriter.newLine();
				}

			}
			trainQueryReader.close();
			FvWriter.close();

			/*
			 * Train a model with SVM
			 */
			String svmC = params.get("letor:svmRankParamC");
			String execPath = params.get("letor:svmRankLearnPath");
			String svmModelFile = params.get("letor:svmRankModelFile");
			String trainFvFile = params.get("letor:trainingFeatureVectorsFile");

			// Run SVM software
			Process cmdProc = Runtime.getRuntime().exec(
					new String[] { execPath, "-c", String.valueOf(svmC),
							trainFvFile, svmModelFile });

			// consume stdout and print it out for debugging purposes
			BufferedReader stdoutReader = new BufferedReader(
					new InputStreamReader(cmdProc.getInputStream()));
			String svmLine;
			while ((svmLine = stdoutReader.readLine()) != null) {
				System.out.println(svmLine);
			}

			// consume stderr and print it for debugging purposes
			BufferedReader stderrReader = new BufferedReader(
					new InputStreamReader(cmdProc.getErrorStream()));
			while ((svmLine = stderrReader.readLine()) != null) {
				System.out.println(svmLine);
			}

			// get the return value from the executable. 0 means success,
			// non-zero
			// indicates a problem
			int retValue = cmdProc.waitFor();
			if (retValue != 0) {
				throw new Exception("SVM Rank crashed.");
			}

			
			
			/*
			 * Classification for the test query
			 */
			BufferedReader testQueryReader = new BufferedReader(new FileReader(
					new File(params.get("queryFilePath"))));
			String testQueryLine = null;
			
			// BufferedWriter topFvListWriter = null;
			
			HashMap<String, Integer> qidDocNumHM = new HashMap<String, Integer>();
			HashMap<Integer, String> indexQidHM = new HashMap<Integer, String>();
			BufferedWriter topFvListWriter = new BufferedWriter(
					new FileWriter(new File(
							params.get("letor:testingFeatureVectorsFile"))));
			ArrayList<ArrayList<Integer>> svmIntDocIDList = new ArrayList<ArrayList<Integer>>();
			int index = 0;
			while ((testQueryLine = testQueryReader.readLine()) != null) {

				// Get initial ranking for test query by BM25
				String[] testQueryFields = testQueryLine.split(":");
				String testQueryID = testQueryFields[0];
				String testQueryContent = testQueryFields[1];
				String[] testQueryTokens = tokenizeQuery(testQueryContent);
				
				// Put the queryID into indexQidHM
				indexQidHM.put(index, testQueryID);
				index ++;

				Qryop qTree;
				RetrievalModelBM25 bm25Model = null;
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
				else if (Double.parseDouble(b) < 0
						|| Double.parseDouble(b) > 1.0)
					System.out
							.println("Error: parameter b should be in the range 0.0-1.0.");
				else {
					double doublek1 = Double.parseDouble(k1);
					double doublek3 = Double.parseDouble(k3);
					double doubleb = Double.parseDouble(b);
					bm25Model = new RetrievalModelBM25((double) doublek1,
							doubleb, doublek3);
				}
				testQueryContent = "#SUM(" + testQueryContent + ")";
				qTree = parseQuery(testQueryContent);
				QryResult result = qTree.evaluate(bm25Model);

				// Sort result
				HashMap<Integer, String> extIDHM = null;
				ExtDocId extDocId;
				if (result != null) {
					extDocId = new ExtDocId(result);
					extDocId.store();
					extIDHM = extDocId.getHM();
					result.sortResult(extIDHM);
				}

				// Store the top 100 doc internal ID into an arraylist
				ArrayList<Integer> svmIntDocID = new ArrayList<Integer>();
				int docNum = Math.min(100, result.docScores.scores.size());
				//System.err.println(docNum);
				qidDocNumHM.put(testQueryID, docNum); //put qid and docNum into qidDocNumHM
				for (int i = 0; i < docNum; i++) {
					svmIntDocID.add(result.docScores.getDocid(i));
				}
				svmIntDocIDList.add(svmIntDocID);

				// Calculate FV(Arraylist of Double) for top 100 doc
				ArrayList<ArrayList<Double>> TopFvList = new ArrayList<ArrayList<Double>>();
				FeatureVector fv = new FeatureVector();
				fv.pageRankHash = pageRankHash;
				fv.disableArray = disableArray;
				fv.tokens = testQueryTokens;
				fv.r = (RetrievalModelLetor) model;
				for (int i = 0; i < docNum; i++) {
					int internalDocId = svmIntDocID.get(i);
					ArrayList<Double> newFV = fv.createFV(internalDocId);
					TopFvList.add(newFV);
				}
				ArrayList<String> normalizedFvList = fv.normalizedFv(TopFvList);

				
				// Write Top 100 FV list
				for (int i = 0; i < docNum; i++) {
					String extId = getExternalDocid(svmIntDocID.get(i));
					StringBuilder strBuilder = new StringBuilder();
					strBuilder.append(0).append(" qid:").append(testQueryID)
							.append(" ").append(normalizedFvList.get(i))
							.append("# ").append(extId).append('\n');
					topFvListWriter.write(strBuilder.toString());
					// topFvListWriter.newLine();
				}
			}
			
			try {
				topFvListWriter.close();
			} catch (Exception e) {
				throw new Exception(
						"Error occurs when try to close topFvListWriter");
			}

			
			
			/*
			 * Rerank test data
			 */
			// Call SVM software for testing
			
			String classifyPath = params.get("letor:svmRankClassifyPath");
			String testingFvFile = params.get("letor:testingFeatureVectorsFile");
			String rankModelFile = params.get("letor:svmRankModelFile");
			String testDocScore = params.get("letor:testingDocumentScores");
			
			Process cmdProc2 = Runtime.getRuntime().exec(
					new String[] { classifyPath, testingFvFile,
							rankModelFile, testDocScore });

			BufferedReader stdoutReader2 = new BufferedReader(
					new InputStreamReader(cmdProc.getInputStream()));
			String svmLine2;
			while ((svmLine2 = stdoutReader2.readLine()) != null) {
				System.out.println(svmLine2);
			}
			
			BufferedReader stderrReader2 = new BufferedReader(
					new InputStreamReader(cmdProc2.getErrorStream()));
			while ((svmLine2 = stderrReader2.readLine()) != null) {
				System.out.println(svmLine2);
			}

			int retValue2 = cmdProc2.waitFor();
			if (retValue2 != 0) {
				throw new Exception("SVM Rank crashed.");
			}
			
			
			// Read scores produced by SVM
			BufferedReader classifiedScoreReader = new BufferedReader(
					new FileReader(new File(
							params.get("letor:testingDocumentScores"))));
			String classifiedScoreLine = null;
			StringBuilder strPrint = new StringBuilder();
			int hmSize = indexQidHM.size();
			// System.err.println(indexQidHM.size());
			
			for (int i = 0; i < hmSize; i++) {
				String qid = indexQidHM.get(i);
				// System.err.println(qid);
				int docNum = qidDocNumHM.get(qid);
				ArrayList<Integer> svmIntDocID =  svmIntDocIDList.get(i);
				
				// 
				QryResult reRankResult = new QryResult();
				//int i = 0;
				//while ((classifiedScoreLine = classifiedScoreReader.readLine()) != null) {
				for (int j = 0; j < docNum; j++) {
					classifiedScoreLine = classifiedScoreReader.readLine().trim();
					//classifiedScoreLine.trim();
					if (classifiedScoreLine.equalsIgnoreCase("nan")) {
						reRankResult.docScores.add(svmIntDocID.get(j), 0.0);
					} else {
						double score = Double.parseDouble(classifiedScoreLine);
						reRankResult.docScores.add(svmIntDocID.get(j), score);
					}
			
				}
				
				// Rerank the top docs by scores giving by SVM classification
				StringBuffer strBuffer = new StringBuffer();
				strBuffer = printResults(qid, reRankResult);
				System.out.println(strBuffer);
				strPrint.append(strBuffer);				
			}
			classifiedScoreReader.close();


			/* write the output to the file given the output path */
			BufferedWriter bufferWriter = null;

			try {
				File file = new File(params.get("trecEvalOutputPath"));
				FileWriter fileWriter = new FileWriter(file);
				bufferWriter = new BufferedWriter(fileWriter);
				bufferWriter.write(strPrint.toString());
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					bufferWriter.close();
				} catch (Exception e) {
				}
			}

		}

		else {
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
					QryResult fbResult = expansion(fbInitialResult, content,
							model, queryID, fbExpansionQueryFile, fbDocs,
							fbTerms, fbMu, fbOrigWeight, expansionOutputBuffer);
					strBuffer = printResults(queryID, fbResult);
				}

				else if (fb) {
					QryResult fbResult = expansion(result, content, model,
							queryID, fbExpansionQueryFile, fbDocs, fbTerms,
							fbMu, fbOrigWeight, expansionOutputBuffer);
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
		}

		// Lookup the document length of the body field of doc 0.
		// System.out.println(s.getDocLength("body", 0));

		// How to use the term vector.
		/*
		 * TermVector tv = new TermVector(1, "body");
		 * System.out.println(tv.stemString(10)); // get the string for the 10th
		 * stem System.out.println(tv.stemDf(10)); // get its df
		 * System.out.println(tv.totalStemFreq(10)); // get its ctf
		 */

		printMemoryUsage(false);

	}

	static QryResult expansion(QryResult result, String origQuery,
			RetrievalModel model, String queryID, String fbExpansionQueryFile,
			int fbDocs, int fbTerms, int fbMu, double fbOrigWeight,
			StringBuffer expansionOutputBuffer) throws Exception {

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
			// writeExpandedQuery(origQuery, fbExpansionQueryFile);
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

		/*
		 * get all the doclen and store them into a list so that we can use it
		 * to calculate default scores later
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
			double docScore = result.docScores.getDocidScore(i);// ?

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
						ptd_default = ((double) fbMu * ptc)
								/ ((double) doclen_k + (double) fbMu);
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

		// writeExpandedQuery(expansionOutput, fbExpansionQueryFile);

		return fbResult;
	}

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
