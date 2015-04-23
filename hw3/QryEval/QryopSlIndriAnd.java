import java.io.*;
import java.util.*;

public class QryopSlIndriAnd extends QryopSl{

	public QryopSlIndriAnd(Qryop... q) {
		for (int i=0; i<q.length; i++)
			this.args.add(q[i]);
	}


	@Override
	public void add(Qryop q) throws IOException {
		// TODO Auto-generated method stub
		this.args.add(q);
	}

	@Override
	public QryResult evaluate(RetrievalModel r) throws IOException {

		allocArgPtrs (r);
		QryResult result = new QryResult ();
	  
		int argSize = this.argPtrs.size();
		int argCounter = argSize;
	    ArgPtr[] argPtrArray = new ArgPtr[argSize];

	    // Initialize argPtrArray
	    for (int j = 0; j < argSize; j++) {
			argPtrArray[j] = this.argPtrs.get(j);
	    }   
	    
	    while (argCounter > 0) {
			int minDocid = Integer.MAX_VALUE;
			double score = 1.0;
			
			// Find min docid
			for (int i = 0; i < argSize; i++) {
				ArgPtr curPtr = argPtrArray[i];
				if (curPtr.nextDoc >= curPtr.scoreList.scores.size()) {
					continue;
				}
				int docid = curPtr.scoreList.getDocid(curPtr.nextDoc); 
				if (docid < minDocid) {
					minDocid = docid;
				}
			}
			
			// check if all the scorelist have done (empty list) 
			if (minDocid == Integer.MAX_VALUE) 
				break;
			
			// Calculate score
			for (int i = 0; i < argSize; i++) {
				
				ArgPtr curPtr = argPtrArray[i];
				int docid = Integer.MAX_VALUE;
				
				if (curPtr.nextDoc >= curPtr.scoreList.scores.size()) {
					score *= ((QryopSl)this.args.get(i)).getDefaultScore(r, minDocid);
				}				
				else if (curPtr.nextDoc < curPtr.scoreList.scores.size()) {
					docid = curPtr.scoreList.getDocid(curPtr.nextDoc);
					if (docid == minDocid) {
						score *= curPtr.scoreList.getDocidScore(curPtr.nextDoc);
						curPtr.nextDoc++;
						argPtrArray[i] = curPtr;
						if (curPtr.nextDoc >= curPtr.scoreList.scores.size()) {
							argCounter --;
						}
					} else { // docid > minDocid
						score *= ((QryopSl)this.args.get(i)).getDefaultScore(r, minDocid);
					}
				}				
			}
			
			// add min docid to result
			result.docScores.add (minDocid, Math.pow(score, 1 / (double)argSize));
			
		}
	    
	    freeArgPtrs ();		
	    return result;
	   
	}

	  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {

		   if (r instanceof RetrievalModelIndri) {
		    	int argSize = this.args.size();
		    	double score = 1.0;
		    	for (int i = 0; i < argSize; i++) {
		    		score *= ((QryopSlScore)this.args.get(i)).getDefaultScore(r, docid);
		    	}
		    	return Math.pow(score, 1.0/argSize);
		    }

		    return 0.0;
	  }

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return null;
	}
	
	
}
