/**
 *  This class implements the SCORE operator for all retrieval models.
 *  The single argument to a score operator is a query operator that
 *  produces an inverted list.  The SCORE operator uses this
 *  information to produce a score list that contains document ids and
 *  scores.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

public class QryopSlScore extends QryopSl {

  private String field = "body";
  private double ctf = 0;
  /**
   *  Construct a new SCORE operator.  The SCORE operator accepts just
   *  one argument.
   *  @param q The query operator argument.
   *  @return @link{QryopSlScore}
   */
  public QryopSlScore(Qryop q) {
    this.args.add(q);
  }

  /**
   *  Construct a new SCORE operator.  Allow a SCORE operator to be
   *  created with no arguments.  This simplifies the design of some
   *  query parsing architectures.
   *  @return @link{QryopSlScore}
   */
  public QryopSlScore() {
  }

  /**
   *  Appends an argument to the list of query operator arguments.  This
   *  simplifies the design of some query parsing architectures.
   *  @param q The query argument to append.
   */
  public void add (Qryop a) {
    this.args.add(a);
  }

  
  /**
   *  Evaluate the query operator.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean)
      return (evaluateBoolean (r));
    else if (r instanceof RetrievalModelRankedBoolean)
      return (evaluateBoolean (r));
    else if (r instanceof RetrievalModelBM25)
    	return (evaluateBM25 (r));
    else if (r instanceof RetrievalModelIndri)
    	return (evaluateIndri (r));
   
    return null;
  }

 /**
   *  Evaluate the query operator for boolean retrieval models.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

    // Evaluate the query argument.
    QryResult result = args.get(0).evaluate(r);

    // Each pass of the loop computes a score for one document. Note:
    // If the evaluate operation above returned a score list (which is
    // very possible), this loop gets skipped.
    for (int i = 0; i < result.invertedList.df; i++) {

      // DIFFERENT RETRIEVAL MODELS IMPLEMENT THIS DIFFERENTLY. 
      // Unranked Boolean. All matching documents get a score of 1.0.
      if (r instanceof RetrievalModelUnrankedBoolean)
        result.docScores.add(result.invertedList.postings.get(i).docid, (float) 1.0);
      else if(r instanceof RetrievalModelRankedBoolean)
    	  result.docScores.add(result.invertedList.postings.get(i).docid, 
    			  				(float) (result.invertedList.getTf(i)));
    }


    // The SCORE operator should not return a populated inverted list.
    // If there is one, replace it with an empty inverted list.

    //if (result.invertedList.df > 0)
	//result.invertedList = new InvList();

    return result;
  }

  
  public QryResult evaluateBM25 (RetrievalModel r) throws IOException {
  	  QryResult result = args.get(0).evaluate(r);
  	  
      double score = 0.0;//1.0?
  	  double k1, b, k3;
      k1 = ((RetrievalModelBM25) r).getParameter("k_1");
      b = ((RetrievalModelBM25) r).getParameter("b");
      k3 = ((RetrievalModelBM25) r).getParameter("k_3");

      String field = result.invertedList.field;

      double idf, tfWeight, userWeight;
      DocLengthStore docLenStore = QryEval.dls;
    
	  int df = result.invertedList.df;
	  double numDocs = QryEval.READER.numDocs();
	  idf = Math.log((numDocs - df + 0.5) / (df + 0.5));
	  idf = Math.max(0, idf);
		
	  int qtf = 1;
	  userWeight = (k3+1) * qtf / (k3+qtf);
    
  	  for (int i = 0; i < result.invertedList.df; i++) {
  											  		
  		  int tf = result.invertedList.getTf(i);
  		  long doclen = docLenStore.getDocLength(field, result.invertedList.postings.get(i).docid);
  		  double avg_doclen = (double) QryEval.READER.getSumTotalTermFreq(field) / 
									(double) QryEval.READER.getDocCount(field);
  		  tfWeight = tf / (tf + k1*((1-b) + b*doclen/avg_doclen));
		
  		  score = idf*tfWeight*userWeight;
  		  result.docScores.add(result.invertedList.postings.get(i).docid, (float)score);
  		
	}
  	return result;
  }
  
  public  QryResult evaluateIndri (RetrievalModel r) throws IOException {
	  
	  	QryResult result = args.get(0).evaluate(r);

	  	double mu, lambda;
	  	mu = ((RetrievalModelIndri) r).getMu();
	  	lambda = ((RetrievalModelIndri) r).getLambda();

	  	for (int i = 0; i < result.invertedList.df; i++) {
	  		double score = 0.0;
	  		String field = result.invertedList.field;
	  		this.field = field;
	    
	  		double ctf = result.invertedList.ctf;
	  		this.ctf = ctf;
	  		double ctotallen = QryEval.READER.getSumTotalTermFreq(field);
	  		double mle = ctf / ctotallen;
	  		
	  		double tf = result.invertedList.getTf(i);
	  		
	        DocLengthStore docLenStore = QryEval.dls;
	        long doclen = docLenStore.getDocLength(field, result.invertedList.postings.get(i).docid);
	  		  
	        double part1 = (1 - lambda) * ((tf + mu * mle) / (doclen +mu));
	        double part2 = lambda * mle;
	        
	        score = part1 + part2;
	   
	  		result.docScores.add(result.invertedList.postings.get(i).docid, (float)score);
	  	}

	  	return result; 	

  }
  


  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {
	  
	  if (r instanceof RetrievalModelIndri) {
		 
		  
		  double mu = ((RetrievalModelIndri) r).getMu();
		  double lambda = ((RetrievalModelIndri) r).getLambda();
		  DocLengthStore dls = QryEval.dls;
	      double ctotallen = QryEval.READER.getSumTotalTermFreq(field);
		  double mle = ctf / ctotallen;
		  long doclen = dls.getDocLength(field, (int)docid);
		  double score = (1 - lambda) * (mu * mle) / (doclen + mu) + lambda * mle;
		  
		  return score;
		  
	  }
	  return 0.0;
  }

  /**
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
    
    String result = new String ();

    for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
      result += (i.next().toString() + " ");

    return ("#SCORE( " + result + ")");
  }

}
