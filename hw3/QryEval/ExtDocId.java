import java.io.*;
import java.util.HashMap;


public class ExtDocId {
	QryResult result;
	HashMap<Integer, String> extIDHM = new HashMap<Integer, String>();
	
    public ExtDocId(QryResult result) {
        this.result = result;
    }
    
    // Put internal docid and external docid into HashMap extIDHM
    public void store() throws IOException {
        for(ScoreList.ScoreListEntry sl : result.docScores.scores) {
            int docid = sl.getDocid();          
            extIDHM.put(docid, QryEval.getExternalDocid(docid));
        }
    }
    
    public HashMap<Integer, String> getHM() {
        return extIDHM;
    }
}
