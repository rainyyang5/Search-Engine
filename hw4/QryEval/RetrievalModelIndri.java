
public class RetrievalModelIndri extends RetrievalModel {
	private int mu;
	private double lambda;
	
	public RetrievalModelIndri(int mu, double lambda) {
		this.mu = mu;		
		this.lambda = lambda;
	}
	
	public boolean setParameter (String parameterName, double value) {
		System.err.println("Error: Unknown parameter name for retrieval model" +
						   "Indri: " + parameterName);
		return false;
	}
	
	public boolean setParameter (String parameterName, String value) {
		System.err.println("Error: Unknown parameter name for retrieval model" +
						   "Indri: " + parameterName);
		return false;
	}
	
	 public int getMu () {
		 return mu;
	 }
	 

	 public double getLambda () {
		 return lambda;
	 }
}