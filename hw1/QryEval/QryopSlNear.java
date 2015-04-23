import java.io.*;
import java.util.*;



public class QryopSlNear extends QryopIl {
	
	/**
	 *  It is convenient for the constructor to accept a variable number
	 *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
	 *  @param q A query argument (a query operator).
	 */
	 public QryopSlNear(Qryop... q) {
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

	    if (r instanceof RetrievalModelUnrankedBoolean)
	      return (evaluateBoolean (r));
	    else if (r instanceof RetrievalModelRankedBoolean) {
	      return (evaluateBoolean (r));
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
	    // Each loop process one document that contains the first argument	
	    for ( ; ptr0.nextDoc < ptr0.invList.postings.size(); ptr0.nextDoc ++) {

	        //ptr0Docid contains the first argument
	        int ptr0Docid = ptr0.invList.getDocid (ptr0.nextDoc);
	        // ptr: the ptr array used to mark the index of the doc in each arg's invList
	        ArgPtr[] ptr = new ArgPtr[this.argPtrs.size()];
	      	     
	        //  Do the other query arguments have the ptr0Docid?
	        for (int j=1; j<this.argPtrs.size(); j++) {
	    	   
	        	ptr[j] = this.argPtrs.get(j);
	    	    while (true) {
	    		    if (ptr[j].nextDoc >= ptr[j].invList.postings.size())
	    			    break EVALUATEDOCUMENTS;		// No more docs can match
	    		    else if (ptr[j].invList.getDocid(ptr[j].nextDoc) > ptr0Docid)
	    			    continue EVALUATEDOCUMENTS;	// The ptr0docid can't match.
	    		    else if (ptr[j].invList.getDocid (ptr[j].nextDoc) < ptr0Docid)
	    			    ptr[j].nextDoc ++;			// Not yet at the right doc.
	    		    else {
	    			    break;		// ptrj matches ptr0Docid
	    		    }    			  
	    	    }       	  
	        }    
	      
	        // ptr0Docid contains all the arguments. Check the distance.
	        List<Integer> firstArgPosList = new ArrayList<Integer>(); // store the matched result
	        int[] curPos = new int[this.argPtrs.size()]; // store the position in each arg's invList
	      
	        
	        
	        Vector<Integer> posListPtr0 = ptr0.invList.postings.get(ptr0.nextDoc).positions;
	        // Each loop process one position in arg1's invList
	        LOOPFIRARG:	       
	        for (int i=0; i < posListPtr0.size(); i++) {
	        	int posPtr0 = posListPtr0.get(i);
	    	    int posPrevPtr = posPtr0; // position in the prev argument's invList
	      
	    	    // Checks whether the jth arg has a match
	      	    LOOPPTRJ:
	      	    for (int j=1; j < this.argPtrs.size(); j++) {
	      		    // Ptrj's position list
	      		    Vector<Integer> posListPtrj = ptr[j].invList.postings.get(ptr[j].nextDoc).positions; 
	      		  
	      		    for (int k=curPos[j]; k<posListPtrj.size(); k++) {
	      			    
	      		    	int posPtrj = posListPtrj.get(k);
	      			    curPos[j] = k;
	      			  
	      			    if (posPtrj <= posPrevPtr)
	      				    continue; // increase posPtrj
	      			    else if (posPtrj - posPrevPtr <= this.nearDistance) {
	      				    posPrevPtr = posPtrj; 
	      				    continue LOOPPTRJ; //match! increase j, get the next posListPtrj
	      			    }
	      			    else
	      				    continue LOOPFIRARG; //increase posPtr0
	      		    }  
	      		    break LOOPFIRARG; // The position list is finished. No more match.
	      	    }   
	    	  
	    	    // posPtr0 match, add it to firstArgPosList to store the result
	    	    firstArgPosList.add(posPtr0);
	    	    // increment the point of every arg
	    	    for (int j=1; j<this.argPtrs.size(); j++)
	    	  	    curPos[j]++;
	        }  
	        
	        
	        // store the result
	        if (!firstArgPosList.isEmpty())
	        	result.invertedList.appendPosting(ptr0Docid, firstArgPosList);
	        
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
