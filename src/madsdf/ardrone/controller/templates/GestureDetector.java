/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package madsdf.ardrone.controller.templates;

import madsdf.ardrone.ActionCommand;

public interface GestureDetector {
    public void addVotation(KNN knn, float stddev);
    public ActionCommand decide();
}
   
