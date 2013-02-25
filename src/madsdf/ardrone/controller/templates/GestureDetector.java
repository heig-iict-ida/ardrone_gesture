/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller.templates;

import madsdf.ardrone.ActionCommand;

public interface GestureDetector {
    public void addVotation(KNN knn, float stddev);
    public ActionCommand decide();
    public long getDurationMS();
    
    // If this returns true, getDurationMS will be called to get the duration
    // of an action
    // IF this is false, the actions will last for as long as they are detected
    // by the gesture detector
    public boolean hasActionDuration();
}
   
