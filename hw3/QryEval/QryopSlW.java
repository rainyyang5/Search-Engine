import java.io.IOException;
import java.util.ArrayList;


public abstract class QryopSlW extends QryopSl {
	public ArrayList<Double> weights;
	
	public QryopSlW(Qryop... q) {
		for (int i = 0; i < q.length; i++) {
			this.args.add(q[i]);
		}
		this.weights = new ArrayList<Double>();
	}
	
	public void add(Qryop a) throws IOException {
		this.args.add(a);
	}
	
	public void addWeight(Double w) {
		this.weights.add(w);
	}
	
	
}
