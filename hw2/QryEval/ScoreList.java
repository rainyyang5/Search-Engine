/**
 *  This class implements the document score list data structure
 *  and provides methods for accessing and manipulating them.
 *
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.util.*;
import java.util.Comparator;

public class ScoreList {

  //  A little utilty class to create a <docid, score> object.

  protected class ScoreListEntry {
    private int docid;
    private double score;

    public int getDocid() {
    	return docid;
    }
    
    public double getScore() {
    	return score;
    }
    
    private ScoreListEntry(int docid, double score) {
      this.docid = docid;
      this.score = score;
    }
  }

  List<ScoreListEntry> scores = new ArrayList<ScoreListEntry>();

  /**
   *  Append a document score to a score list.
   *  @param docid An internal document id.
   *  @param score The document's score.
   *  @return void
   */
  public void add(int docid, double score) {
    scores.add(new ScoreListEntry(docid, score));
  }

  /**
   *  Get the n'th document id.
   *  @param n The index of the requested document.
   *  @return The internal document id.
   */
  public int getDocid(int n) {
    return this.scores.get(n).docid;
  }

  /**
   *  Get the score of the n'th document.
   *  @param n The index of the requested document score.
   *  @return The document's score.
   */
  public double getDocidScore(int n) {
    return this.scores.get(n).score;
  }


  
  public void sortResult(final HashMap<Integer, String> extIDHM) {
      Collections.sort(scores, new Comparator<ScoreListEntry>() {
          public int compare(ScoreListEntry sl1, ScoreListEntry sl2) {
              if (sl1.getScore() < sl2.getScore()) {
                  return 1;
              } else if (sl1.getScore() == sl2.getScore()) {
                  String extDocid1, extDocid2;
                  extDocid1 = extIDHM.get(sl1.getDocid());
                  extDocid2 = extIDHM.get(sl2.getDocid());

                  if (extDocid1 != null && extDocid2 != null) {
                      if (extDocid2.compareTo(extDocid1) > 0) 
                    	  return 0;
                      else
                    	  return 1;
                  }
              }
              return 0;
          }
      }
      );
  }

}
