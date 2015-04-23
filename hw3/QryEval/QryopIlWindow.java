import java.awt.List;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;


public class QryopIlWindow extends QryopIl{
	private int distance;

	/**
	 *  It is convenient for the constructor to accept a variable number
	 *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
	 *  @param q A query argument (a query operator).
	 */
	 public QryopIlWindow(int dist, Qryop... q) {
		distance = dist;
		 
	    for (int i = 0; i < q.length; i++)
	        this.args.add(q[i]);
	}
	 
	public QryResult evaluate(RetrievalModel r) throws IOException {
		
		if (r instanceof RetrievalModelBM25) {
	        return (evaluateWindow(r));
		}
		else if(r instanceof RetrievalModelIndri) {
		    return (evaluateWindow(r));
		}
		return null;

		
	}
	
	public QryResult evaluateWindow(RetrievalModel r) throws IOException {
		allocArgPtrs(r);
		QryResult result = new QryResult();
		
		int argSize = this.argPtrs.size();
		if (argSize < 1)
			return result;

		ArgPtr[] argPtrArray = new ArgPtr[argSize];
		InvList.DocPosting[] postingsArray = new InvList.DocPosting[argSize];
		
		// Initialize argPtrArray.
		for (int i = 0; i < argSize; i++)
			argPtrArray[i] = this.argPtrs.get(i);
		
		Qryop.ArgPtr ptr0 = this.argPtrs.get(0);
		
		EVALUATEDOCUMENTS:
		for (; ptr0.nextDoc < ptr0.invList.postings.size(); ptr0.nextDoc++) {
			
			int ptr0Docid = ptr0.invList.getDocid(ptr0.nextDoc);
			int matchDocCnt = 1;
			boolean isSecondArg = true;
			
			for (int j = 1; j < argSize; j++) {
				Qryop.ArgPtr ptrj = this.argPtrs.get(j);
				int ptrjSize = ptrj.invList.postings.size();
				while (true) {
					if (ptrj.nextDoc >= ptrjSize)
						break EVALUATEDOCUMENTS; // No more docs can match
					else if (ptrj.invList.getDocid(ptrj.nextDoc) > ptr0Docid)
						continue EVALUATEDOCUMENTS; // ptr0docid doesn't match
					else if (ptrj.invList.getDocid(ptrj.nextDoc) < ptr0Docid)
						ptrj.nextDoc++; //Not yet at the right doc.
					else {
						matchDocCnt ++;
						if (isSecondArg) {
							postingsArray[0] = ptr0.invList.postings.get(ptr0.nextDoc);
							isSecondArg = false;
						}
						postingsArray[j] = ptrj.invList.postings.get(ptrj.nextDoc);
						if (matchDocCnt == argSize) {
							ArrayList<Integer> matchPos = returnMatchedPos(postingsArray, this.distance);
							if (matchPos.size() > 0) {
								result.invertedList.appendPosting(ptr0Docid, matchPos);
							}
						}
						ptrj.nextDoc++;
						break;
					}
				}
			}
		}
			
		result.invertedList.field = ptr0.invList.field;
		freeArgPtrs();
		return result;
	}

	private ArrayList<Integer> returnMatchedPos(InvList.DocPosting[] postingsArray, int distance) {

		ArrayList<Integer> matchPos = new ArrayList<Integer>();
		int size = postingsArray.length;
		int[] curPointers = new int[size];
		

		
		ITERATE:
		while (true) {
			
			int minPos = Integer.MAX_VALUE;
			int maxPos = Integer.MIN_VALUE;
			int minArg = -1;
			for (int i = 0; i < size; i++) {
				int argiPostingSize = postingsArray[i].positions.size();
				int curPtri = curPointers[i];
				
				if (argiPostingSize <= curPtri)
					break ITERATE;

				int curPosi = postingsArray[i].positions.get(curPtri);
				if (curPosi < minPos) { 
					minPos = curPosi;
					minArg = i;
				}
				if (curPosi > maxPos) {
					maxPos = curPosi;
				}
			}
		
			if (maxPos - minPos + 1 > distance) {
				curPointers[minArg] ++;
			}
			else { // match! add the position of the first arg to return list;
				matchPos.add(postingsArray[0].positions.get(curPointers[0]));
				// increment the pos pointers for all the args.
				for (int i = 0; i < size; i++) {
					curPointers[i] ++;
				}
			}
		
		}
		return matchPos;

	}
	
	@Override
	public void add(Qryop q) throws IOException {
		// TODO Auto-generated method stub
		this.args.add(q);
		
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return null;
	}

}
