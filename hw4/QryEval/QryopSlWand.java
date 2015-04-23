import java.io.IOException;
import java.util.ArrayList;



public class QryopSlWand extends QryopSlW{
	
	
	public QryopSlWand(Qryop... q) {
		for (int i=0; i<q.length; i++)
			this.args.add(q[i]);
		this.weights = new ArrayList<Double>();
	}

	@Override
	public double getDefaultScore(RetrievalModel r, long docid)
			throws IOException {
		double score = 1.0;
		double weight = 0.0;
		double totalWeight = 0.0;
				
		int argSize = this.args.size();
		for (int i = 0; i < argSize; i++) {
			totalWeight += this.weights.get(i);
		}
		
		for (int i = 0; i < argSize; i++) {
			weight = this.weights.get(i);
			score *= Math.pow(((QryopSl) this.args.get(i)).getDefaultScore(r, docid), weight/totalWeight);
		}
		
		if (totalWeight == 0) {
			score= 1.0;
		}
		
		return score;
	}

	@Override
	public void add(Qryop q) throws IOException {
		// TODO Auto-generated method stub
		this.args.add(q);
		
	}

	@Override
	public QryResult evaluate(RetrievalModel r) throws IOException {
		// TODO Auto-generated method stub
	    if (r instanceof RetrievalModelIndri)
	        return (evaluateWand (r));

		return null;
	}
	
	public QryResult evaluateWand(RetrievalModel r) throws IOException {
		double totalWeight = 0.0;
		
		allocArgPtrs (r);
		QryResult result = new QryResult ();
	  
		int argSize = this.argPtrs.size();
		int argCounter = argSize;
	    ArgPtr[] argPtrArray = new ArgPtr[argSize];

	    for (double weight : this.weights) {
	    	totalWeight += weight;
	    }
	    
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
					score *= Math.pow(((QryopSl)this.args.get(i)).getDefaultScore(r, minDocid), this.weights.get(i)/totalWeight);
				}				
				else if (curPtr.nextDoc < curPtr.scoreList.scores.size()) {
					docid = curPtr.scoreList.getDocid(curPtr.nextDoc);
					if (docid == minDocid) {
						score *= Math.pow(curPtr.scoreList.getDocidScore(curPtr.nextDoc), this.weights.get(i)/totalWeight);
						curPtr.nextDoc++;
						argPtrArray[i] = curPtr;
						if (curPtr.nextDoc >= curPtr.scoreList.scores.size()) {
							argCounter --;
						}
					} else { // docid > minDocid
						score *= Math.pow(((QryopSl)this.args.get(i)).getDefaultScore(r, minDocid), this.weights.get(i)/totalWeight);
					}
				}				
			}
			
			// add min docid to result
			result.docScores.add (minDocid, score);
			
		}
	    
	    freeArgPtrs ();		
	    return result;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return null;
	}


}
