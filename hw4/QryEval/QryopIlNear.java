import java.io.*;
import java.util.*;



public class QryopIlNear extends QryopIl {
	


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
	 *  It is convenient for the constructor to accept a variable number
	 *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
	 *  @param q A query argument (a query operator).
	 */
	
	private int distance;
	
	 public QryopIlNear(int dist, Qryop... q) {
		distance = dist;
		 
	    for (int i = 0; i < q.length; i++)
	        this.args.add(q[i]);
	}

	/**
	 *  Evaluates the query operator, including any child operators and
     *  returns the result.
	 *  @param r A retrieval model that controls how the operator behaves.
	 *  @return The result of evaluating the query.
	 *  @throws IOException
	 */
	public QryResult evaluate(RetrievalModel r) throws IOException {

	    if (r instanceof RetrievalModelUnrankedBoolean)
	      return (evaluateBoolean (r));
	    else if (r instanceof RetrievalModelRankedBoolean) {
	      return (evaluateBoolean (r));
	    }
	    else if (r instanceof RetrievalModelBM25) {
	       return (evaluateBoolean(r));
	    }
	    else if(r instanceof RetrievalModelIndri) {
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

	    //  Initialization
	    allocArgPtrs (r);
	    QryResult result = new QryResult();
	    

	    ArgPtr ptr0 = this.argPtrs.get(0);
	    result.invertedList.field = new String(ptr0.invList.field);
	    

	    EVALUATEDOCUMENTS:
			for ( ; ptr0.nextDoc < ptr0.invList.postings.size(); ptr0.nextDoc ++) {
				
				int ptr0Docid = ptr0.invList.getDocid (ptr0.nextDoc);
				ArgPtr[] ptr = new ArgPtr[this.argPtrs.size()-1];
			
			
		  	  	for (int j=0; (j + 1) < this.argPtrs.size(); j++) {
		  	  		ptr[j] = this.argPtrs.get(j + 1);
			  	  	while (true) {
						if (ptr[j].nextDoc >= ptr[j].invList.postings.size())
							break EVALUATEDOCUMENTS;		// No more docs can match
						else if (ptr[j].invList.getDocid (ptr[j].nextDoc) > ptr0Docid)
							continue EVALUATEDOCUMENTS;	// The ptr0docid can't match.
						else if (ptr[j].invList.getDocid (ptr[j].nextDoc) < ptr0Docid)
							ptr[j].nextDoc ++;			// Not yet at the right doc.
						else
							break;				// ptrj matches ptr0Docid
					}
		  	  	}
		  	  	
		  	  	// ptr0Docid exists in all argPtrs, check distance
		  	  	List<Integer> positions = new ArrayList<Integer>();
		  	  	int[] ptriPosArray = new int[this.argPtrs.size()-1];

		  	  	
		  	  	LOOPPTR0POSITION:
		  	  	for (int ptr0Pos : ptr0.invList.postings.get(ptr0.nextDoc).positions) {
		  	  		int prevArgPos = ptr0Pos;
		  	  		
		  	  		LOOPPTRI:
			  	  	for (int i=0; i < this.argPtrs.size() - 1; i++) {
			  	  		
			  	  		Vector<Integer> ptriPosList = ptr[i].invList.postings.get(ptr[i].nextDoc).positions;

				  	  	for (int j = ptriPosArray[i]; j < ptriPosList.size(); j++) {
				  	  		int ptriPos = ptriPosList.get(j);
				  	  		ptriPosArray[i] = j;
				  	  		
				  	  		if (ptriPos <= prevArgPos) {				  	  			
				  	  			continue; // increment ptriPos to place it at the position after prevArgPos
				  	  		} else if (ptriPos - prevArgPos <= distance) {				  	  		
				  	  			prevArgPos = ptriPos;
				  	  			continue LOOPPTRI; // match! check next arg
				  	  		} else {
				  	  			continue LOOPPTR0POSITION; //imcrement ptr0Pos to check next position of first arg
				  	  		}
				  	  	}
				  	  	break LOOPPTR0POSITION;
			  	  	}
			  	  	
			  	  	// store matched ptr0Pos
			  	  	positions.add(ptr0Pos);
			  	  	
			  	  	// increment position in all args
			  	  	for (int i=0; i < this.argPtrs.size() - 1; i++) {
			  	  		ptriPosArray[i]++;
			  	  	}
		  	  	}
		  	  	
		  	  	//add to result (inverted list)
		  	  	if (!positions.isEmpty()) {
		  	  		result.invertedList.appendPosting(ptr0Docid, positions);
		  	  	}
			}
	     
	    freeArgPtrs ();
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

	    return ("#NEAR/" + this.nearDistance + "(" + result + ")");
	  }


}
