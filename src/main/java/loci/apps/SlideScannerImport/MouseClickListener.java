/*
 * #%L
 * Aperio and Ventana slide scanner importer plugin for ImageJ.
 * %%
 * Copyright (C) 2014 Board of Regents of the University of
 * Wisconsin-Madison.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package loci.apps.SlideScannerImport;

import ij.ImagePlus;
import ij.gui.ImageCanvas;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class MouseClickListener implements MouseListener, MouseMotionListener{

	double mouseX, mouseY;
	boolean mouseClicked;
	ImagePlus imp;

	public MouseClickListener(ImagePlus imp) {
		mouseX = 0; mouseY = 0;
		mouseClicked = false;
		this.imp = imp;
		imp.getWindow().getCanvas().addMouseListener(this);
		imp.getWindow().getCanvas().addMouseMotionListener(this);
	}

	public double[] getClickCoordinates(){
		double[] retVal = {mouseX, mouseY};
		return retVal;
	}

	public boolean getMouseClicked(){
		return mouseClicked;
	}

	@Override
	public void mouseDragged(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseMoved(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseClicked(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseExited(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		// TODO Auto-generated method stub
		ImageCanvas canvas = (ImageCanvas) arg0.getSource();

		// Convert global coords to local
		mouseX = canvas.offScreenXD(arg0.getX());
		mouseY = canvas.offScreenYD(arg0.getY());

		mouseClicked = true;
	}


}
