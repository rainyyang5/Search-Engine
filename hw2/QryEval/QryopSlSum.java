import java.io.*;
import java.util.*;


public class QryopSlSum extends QryopSl{
	

	  /**
	   *  It is convenient for the constructor to accept a variable number
	   *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
	   *  @param q A query argument (a query operator).
	   */
	  public QryopSlSum(Qryop... q) {
	    for (int i = 0; i < q.length; i++)
	      this.args.add(q[i]);
	  }

	  /**
	   *  Appends an argument to the list of query operator arguments.  This
	   *  simplifies the design of some query parsing architectures.
	   *  @param {q} q The query argument (query operator) to append.
	   *  @return void
	   *  @throws IOException
	   */
	  public void add (Qryop a) {
	    this.args.add(a);
	  }

  /**
   *  Evaluates the query operator, including any child operators and
   *  returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

	  if (r instanceof RetrievalModelBM25) 
		  return (evaluateBM25(r));
	  return null;
  };
  
  public QryResult evaluateBM25(RetrievalModel r) throws IOException {
	  
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
				  score = score + prevScore;
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

  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {

	    if (r instanceof RetrievalModelBM25)
	      return (0.0);

	    return 0.0;
  }
  
  /*
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString() {
  	String result = new String();
  	for (int i = 0; i < this.args.size(); i++)
  		result += this.args.get(i).toString() + " ";
  	
  	return ("#SUM( " + result + ")");
  }
  
}