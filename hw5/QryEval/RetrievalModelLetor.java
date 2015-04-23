public class RetrievalModelLetor extends RetrievalModel {

	private double mu;
	private double lambda;
	private double k1;
	private double k3;
	private double b;

	public RetrievalModelLetor(double mu, double lambda, double k1, double b,
			double k3) {
		this.mu = mu;
		this.lambda = lambda;
		this.k1 = k1;
		this.b = b;
		this.k3 = k3;
	}

	@Override
	public boolean setParameter(String parameterName, double value) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean setParameter(String parameterName, String value) {
		// TODO Auto-generated method stub
		return false;
	}

	public double getParameter(String parameterName) {
		if (parameterName.equals("mu")) {
			return (double)mu;
		} else if (parameterName.equals("lambda")) {
			return lambda;
		}
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
