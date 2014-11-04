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
