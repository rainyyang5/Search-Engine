
public class RetrievalModelBM25 extends RetrievalModel {
	private double k1;
	private double k3;
	private double b;
	
	public RetrievalModelBM25(double k1, double b, double k3) {
		this.k1 = k1;		
		this.b = b;
		this.k3 = k3;
	}
	
	public boolean setParameter (String parameterName, double value) {
		System.err.println("Error: Unknown parameter name for retrieval model" +
						   "BM25: " + parameterName);
		return false;
	}
	
	public boolean setParameter (String parameterName, String value) {
		System.err.println("Error: Unknown parameter name for retrieval model" +
						   "BM25: " + parameterName);
		return false;
	}

	 public double getParameter (String parameterName) {
		  if (parameterName.equals("k_1")) {
			  return k1;
		  } else if (parameterName.equals("b")) {
			  return b;
		  } else if (parameterName.equals("k_3")) {
			  return k3;
		  } else {
			  return -1;
		  }
	  }
	
}