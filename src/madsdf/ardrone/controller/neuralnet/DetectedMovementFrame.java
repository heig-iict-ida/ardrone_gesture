package madsdf.ardrone.controller.neuralnet;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.LinkedList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;

/**
 * Allow the user to select the captor and the service he want to use
 *
 * Java version : JDK 1.6.0_21 IDE : Netbeans 7.1.1
 *
 * @author Gregoire Aubert
 * @version 1.0
 */
public class DetectedMovementFrame extends javax.swing.JFrame {

    private static final long serialVersionUID = 1L;
    // The default frame size
    public static int DEFAULT_FRAME_WIDTH = 262;
    public static int DEFAULT_FRAME_HEIGHT = 147;

    /**
     * Creates new form MainFrame
     *
     * @param controller the NeuralController that create the
     * CaptorSelectionFrame
     */
    public DetectedMovementFrame(String title) {
        initComponents();
        // Show the frame
        this.setTitle(title);
        this.setVisible(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        lblMoveTitle = new javax.swing.JLabel();
        lblMove = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Shimmer and Neural Network");
        setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("gesture_icon.png")));
        setMinimumSize(new java.awt.Dimension(232, 127));
        setResizable(false);

        lblMoveTitle.setText("Detected gesture :");

        lblMove.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        lblMove.setText("-");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblMove, javax.swing.GroupLayout.DEFAULT_SIZE, 268, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblMoveTitle)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lblMoveTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblMove)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Change the action command detected
     *
     * @param action is the name of the detected action command
     */
    public void changeActionCommand(String action) {
        lblMove.setText(action);
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel lblMove;
    private javax.swing.JLabel lblMoveTitle;
    // End of variables declaration//GEN-END:variables
}
