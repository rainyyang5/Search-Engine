public class RetrievalModelRankedBoolean extends RetrievalModel {
	public boolean setParameter (String parameterName, double value) {
		System.err.println("Error: Unknown parameter name for retrieval model" +
						   "RankedBoolean: " + parameterName);
		return false;
	}
	
	public boolean setParameter (String parameterName, String value) {
		System.err.println("Error: Unknown parameter name for retrieval model" +
						   "RankedBoolean: " + parameterName);
		return false;
	}
}
		
