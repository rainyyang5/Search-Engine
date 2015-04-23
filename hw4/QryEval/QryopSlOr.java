import java.io.*;
import java.util.*;

public class QryopSlOr extends QryopSl {
	
	public boolean rankedFlag = false;
	
	public QryopSlOr(Qryop... q) {
		for (int i = 0; i < q.length; i++)
			this.args.add(q[i]);
	}
	
	public void add (Qryop a){
		this.args.add(a);
	}
	
	public QryResult evaluate(RetrievalModel r) throws IOException {
		if (r instanceof RetrievalModelUnrankedBoolean)
			return (evaluateBoolean(r));
		else if (r instanceof RetrievalModelRankedBoolean) {
			rankedFlag = true;
			return (evaluateBoolean(r));
		}
		return null;
	}

	/**
	 *  Evaluates the query operator for boolean retrieval models,
	 *  including any child operators and returns the result.
	 *  @param r A retrieval model that controls how the operator behaves.
	 *  @return The result of evaluating the query.
	 *  @throws IOException
	 */
	public QryResult evaluateBoolean (RetrievalModel r) throws IOException {
		
		allocArgPtrs(r);
		QryResult result = new QryResult();
		HashMap<Integer, Double> idScoreHM = new HashMap<Integer, Double>();
		
		for (int i=0; i < this.argPtrs.size(); i++) {
			
			ArgPtr ptri = this.argPtrs.get(i);
			
			for( ; ptri.nextDoc < ptri.scoreList.scores.size(); ptri.nextDoc++) {
				int docid = ptri.scoreList.getDocid(ptri.nextDoc);
				double score = ptri.scoreList.getDocidScore(ptri.nextDoc);
				if (idScoreHM.get(docid) == null)
					idScoreHM.put (docid, score);
				else {
					double prevScore = idScoreHM.get(docid);
					if (prevScore < score)
						idScoreHM.put(docid, score);										
				}						
			}
		}
		
		Iterator hashItr = idScoreHM.entrySet().iterator();
		while (hashItr.hasNext()) {
			Map.Entry<Integer, Double> pair = (Map.Entry) hashItr.next();
			result.docScores.add(pair.getKey(), pair.getValue());
		}


		freeArgPtrs();
		return result;
	}
		
	/*
	 *  Calculate the default score for the specified document if it
	 *  does not match the query operator.  This score is 0 for many
	 *  retrieval models, but not all retrieval models.
	 *  @param r A retrieval model that controls how the operator behaves.
	 *  @param docid The internal id of the document that needs a default score.
	 *  @return The default score.
	 */
	public double getDefaultScore (RetrievalModel r, long docid) throws IOException {

		if (r instanceof RetrievalModelUnrankedBoolean)
			return (0.0);

	    return 0.0;
	}

	/*
	 *  Return a string version of this query operator.  
	 *  @return The string version of this query operator.
	 */
	public String toString(){
		    
		String result = new String ();

		for (int i=0; i<this.args.size(); i++)
		    result += this.args.get(i).toString() + " ";

		return ("#OR( " + result + ")");
	}


}
