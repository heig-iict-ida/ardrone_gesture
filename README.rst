=======================
ARDrone Gesture Control
=======================
A Java library to control a Parrot ARDrone_ using Shimmer_ accelerometer sensor placed on the wrists.
The library should be flexible enough so it can be adapted to other sensor types or such that only the ARDrone control part can be used.

This software was developped at the `HEIG-VD <http://www.heig-vd.ch>`_ and presented (and tried by children)
during the opening day of the "Souvenirs du Futur" exhibition at `La Maison d'Ailleurs <http://www.ailleurs.ch>`_

.. image:: docs/images/all_screen_small.png

ARDrone control
---------------
The ARDrone control part is implemented in `Project_Quadropter`. Most of the
interesting stuff is in the madsdf.ardrone package. This has been tested on
version 1 of the ARDrone.

Gesture recognition
-------------------
Gesture recognition works by comparing (windowed) incoming accelerometer data
with a set of gesture templates. When the distance between the current data
and a template is below a threshold, we recognize the gesture.

More precisely, we use a `K-nearest neighbors` classifier with `k=3` to classify
gesture. We also impose a threshold on the standard deviation of the window
to avoid gesture recognition when the user is at rest.

Online gesture detection
........................
Online gesture detection is performed in `Projet_Quadropter`. A window as shown
below is displayed for each Shimmer. It shows the distance to gesture templates,
the votes of the closest neighbors, the standard deviation for the current window
and the detected gesture (if any).

.. image:: docs/images/detection_window_highlighted_small.png

Project Setup Notes
===================
This project requires that you add ShimmerMoveAnalyzer as a dependency (in
netbeans, go to libraries/compile -> Add Project).

Also, you need to add all jars in ShimmerMoveAnalyzer/lib/jogl/ to your
runtime classpath (go to libraries/run -> Add JAR/Folder and add all the jars
in jogl)

Credits
.......
Copyright 2013 HEIG-VD

Written by Grégoire Aubert and Julien Rebetez

With advices from Andres Perez-Uribe and Héctor Satizábal

.. _ARDrone: http://ardrone2.parrot.com/
.. _Shimmer: http://www.shimmer-research.com/



