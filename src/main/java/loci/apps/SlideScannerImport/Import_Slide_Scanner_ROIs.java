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

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.macro.Interpreter;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;

import java.awt.FileDialog;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import loci.plugins.in.ImagePlusReader;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import loci.plugins.in.ImporterPrompter;

public class Import_Slide_Scanner_ROIs implements PlugIn {

	ImagePlus fullSlideImage, lowresScanImage;
	String fullFilePath;
	double[] zoomRatioXY, PPM_WISCSCAN_XY, PPM_SLIDE_XY;
	float[] avgROIzoomRatioXY, tissueLocationOnSlide;
	ArrayList<ArrayList<Float>> rois;
	FloatPolygon lowresSIFTmatches, slideSIFTmatches;
	AperioScannerInterpreter AperioInterpreter;
	VentanaScannerInterpreter VentanaInterpreter;

	final String[] supportedFormats = {"Aperio", "Ventana"};
	String chosenFormat="";

	public static void main(String[] args){
		new ImageJ();
		new IJ();

		Import_Slide_Scanner_ROIs ssRoi = new Import_Slide_Scanner_ROIs();
		ssRoi.run(null);
	}

	public Import_Slide_Scanner_ROIs(){

	}


	@Override
	public void run(String arg0) {

		if(!createDialog()) return;

		findCorrespondences();
		tissueLocationOnSlide = calculateTissueLocationOnSlide();
		rois = placeROISonSlideImage(true);
		rois = recreateROIListRelativeToStartPoint();
		calculateSlidePPM();
		writeWiscScanXYZ();
		lowresScanImage.close();

	}

	private boolean createDialog(){

		try{
			GenericDialog gd = new GenericDialog("Select Format:");
			gd.addNumericField ("WiscScan pixels/micron with current objective and zoom: ",  0, 0);
			gd.addMessage ("Select from these supported formats:");
			gd.addChoice("Supported Formats:", supportedFormats, null);

			PPM_WISCSCAN_XY = new double[2];
			PPM_SLIDE_XY = new double[2];
			
			boolean goodToContinue = false;
			while(!goodToContinue){
				gd.showDialog();
				if (gd.wasCanceled()) return false;
				
				PPM_WISCSCAN_XY[0] = gd.getNextNumber();
				if(PPM_WISCSCAN_XY[0] >0) goodToContinue = true;
				else IJ.showMessage("Please enter a valid value for pixels/micron");
			}

			PPM_WISCSCAN_XY[1] = PPM_WISCSCAN_XY[0];
			chosenFormat = supportedFormats[gd.getNextChoiceIndex()];

		} catch(Throwable e){
			IJ.log("Error encountered while parsing dialog box.");
			IJ.log(e.getStackTrace().toString());
			Interpreter.batchMode=false;
		}

		if(chosenFormat.equalsIgnoreCase("Ventana")){		
			try{
				GenericDialog gd2 = new GenericDialog("Import Ventana ROIs");
				gd2.addMessage ("Please run the Ventana Slide Viewer software to get details	about the largest image.");
				gd2.addNumericField ("Tissue Size X (pixels)",  40000, 0);	
				gd2.addNumericField ("Tissue Size Y (pixels)",  70000, 0);
				gd2.addMessage ("Please run WiscScan and measure the dimensions of the slide (in microns).");
				gd2.addNumericField ("Slide Size X (microns)",  1600, 0);
				gd2.addNumericField ("Slide Size Y (microns)",  8000, 0);

				gd2.showDialog();
				if (gd2.wasCanceled()) return false;

				//				bigTissuePicSizeX = gd2.getNextNumber();
				//				bigTissuePicSizeY = gd2.getNextNumber();
				//				slideActualMicronSizeX =(float) gd2.getNextNumber();
				//				slideActualMicronSizeY = (float) gd2.getNextNumber();

				return true;
			}catch(Throwable e){
				IJ.log("Error encountered while parsing dialog box.");
				IJ.log(e.getStackTrace().toString());
				Interpreter.batchMode=false;
			}
		}


		else if(chosenFormat.equalsIgnoreCase("Aperio")){
			try{
				GenericDialog gd2 = new GenericDialog("Import Aperio ROIs");
				gd2.addMessage("Please ensure that the .SVS file you want to import and its corresponding annotations file (.XML file) have\n the same name and are in the same directory, and press OK");
				gd2.setOKLabel("Import File");
				gd2.showDialog();
				if(gd2.wasCanceled()) return false;

				fullFilePath = FileChooser("Aperio");
				if(fullFilePath.isEmpty()) return false;
				String xmlpath = fullFilePath.substring(0, fullFilePath.indexOf(".svs")) + ".xml";

				AperioInterpreter  = new AperioScannerInterpreter(fullFilePath, xmlpath);

				fullSlideImage = AperioInterpreter.getFullSlideImage();
				lowresScanImage = AperioInterpreter.getLowResScanImage();
				rois = AperioInterpreter.getROIsList();
				zoomRatioXY = AperioInterpreter.getZoomRatioXY();		
				
				return true;

			} catch(Throwable e){
				IJ.log("Error encountered while parsing dialog box.");
				IJ.log(e.getStackTrace().toString());
				Interpreter.batchMode=false;
			}
		}

		return false;
	}

	private String FileChooser(String format){
		FileDialog fd = new FileDialog(IJ.getInstance(), "Choose a file", FileDialog.LOAD);
		fd.setDirectory("C:\\");
		if(format.equalsIgnoreCase("Ventana"))
			fd.setFile("*.");
		else if(format.equalsIgnoreCase("Aperio"))
			fd.setFile("*.svs");
		fd.setVisible(true);
		return fd.getDirectory() + fd.getFile();
	}

	private void findCorrespondences(){
		/*
		 * Find the corresponding SIFT matches
		 */
		SIFT_ExtractPointRoi sift = new SIFT_ExtractPointRoi();
		lowresScanImage.show();
		fullSlideImage.show();

		sift.exec(lowresScanImage, fullSlideImage, 2);

		Roi thumbROIS = lowresScanImage.getRoi();
		Roi slideROIS = fullSlideImage.getRoi();
		lowresSIFTmatches = thumbROIS.getFloatPolygon();
		slideSIFTmatches = slideROIS.getFloatPolygon();		

	}

	private float[] calculateTissueLocationOnSlide(){
		return calculateTissueLocationOnSlide(lowresSIFTmatches, slideSIFTmatches);
	}

	private float[] calculateTissueLocationOnSlide(FloatPolygon thumbnailPolygon, FloatPolygon slidePolygon){

		float[] xThumb = thumbnailPolygon.xpoints;
		float[] yThumb = thumbnailPolygon.ypoints;
		float[] xSlide = slidePolygon.xpoints;
		float[] ySlide = slidePolygon.ypoints;

		if(avgROIzoomRatioXY == null) avgROIzoomRatioXY = new float[2];
		avgROIzoomRatioXY[0] = 0;
		avgROIzoomRatioXY[1] = 0;

		float thumbSizeX = lowresScanImage.getWidth();


		int npoints = thumbnailPolygon.npoints-1;
		float[] xRatio = new float[npoints];
		float[] yRatio = new float[npoints];
		float[] xStartPoints = new float[npoints];
		float[] yStartPoints = new float[npoints];
		float xAverage = 0, yAverage = 0, xStd = 0, yStd = 0, xFinal = 0, yFinal = 0;
		float ratioAverageX = 0, ratioAverageY = 0, ratioStdX = 0, ratioStdY = 0;


		//calculate the ratio of a line between two points in the Slide image and Thumbnail image (gives x and y scaling)
		//then use this scaling factor to calculate the estimated start point for each.
		for(int i=0; i<npoints; i++){
			xRatio[i] = (xSlide[i+1] - xSlide[i]) / ((thumbSizeX - xThumb[i+1]) - (thumbSizeX - xThumb[i]));
			yRatio[i] = (ySlide[i+1] - ySlide[i]) / (yThumb[i+1] - yThumb[i]);
			ratioAverageX += xRatio[i];
			ratioAverageY += yRatio[i];
			xStartPoints[i] = xSlide[i] - ((thumbSizeX - xThumb[i]) * xRatio[i]);
			yStartPoints[i] = ySlide[i] - (yThumb[i] * yRatio[i]);
			xAverage += xStartPoints[i];
			yAverage += yStartPoints[i];
		}

		xAverage /= npoints;
		yAverage /= npoints;
		ratioAverageX /= npoints;
		ratioAverageY /= npoints;

		//The above calculated start point has a few outliers that throw off estimation badly, so calculate standard dev.
		for(int i=0; i<npoints; i++){
			xStd = (float) (xStd + Math.pow(xStartPoints[i] - xAverage, 2));
			yStd = (float) (yStd + Math.pow(yStartPoints[i] - yAverage, 2));

			ratioStdX = (float) (ratioStdX + Math.pow(xRatio[i] - ratioAverageX, 2));
			ratioStdY = (float) (ratioStdY + Math.pow(yRatio[i] - ratioAverageY, 2));
		}

		xStd = (float) Math.sqrt(xStd / npoints);
		yStd = (float) Math.sqrt(yStd / npoints);
		ratioStdX = (float) Math.sqrt(ratioStdX / npoints);
		ratioStdY = (float) Math.sqrt(ratioStdY / npoints);


		//and only take the average of values inside the standard deviation. Gives MUCH better estimate.
		int xCounter = 0, yCounter = 0, ratioCounterX = 0, ratioCounterY = 0;
		for(int i=0; i<npoints; i++){
			if(Math.abs(xStartPoints[i] - xAverage) < xStd){
				xFinal += xStartPoints[i];
				xCounter++;
			}
			if(Math.abs(yStartPoints[i] - yAverage) < yStd){
				yFinal += yStartPoints[i];
				yCounter++;
			}

			if(Math.abs(xRatio[i] - ratioAverageX) < ratioStdX){
				avgROIzoomRatioXY[0] += xRatio[i];
				ratioCounterX++;
			}
			if(Math.abs(yRatio[i] - ratioAverageY) < ratioStdY){
				avgROIzoomRatioXY[1] += yRatio[i];
				ratioCounterY++;
			}
		}

		avgROIzoomRatioXY[0] /= ratioCounterX;
		avgROIzoomRatioXY[1] /= ratioCounterY;

		float[] retVal = new float[2];
		retVal[0] = (xFinal/xCounter) - thumbSizeX*Math.abs(avgROIzoomRatioXY[0]);
		retVal[1] = yFinal/yCounter;

		return retVal;

	}

	private ArrayList<ArrayList<Float>> placeROISonSlideImage(boolean showOnImage){

		ArrayList<ArrayList<Float>> retVal = new ArrayList<ArrayList<Float>>();
		//ArrayList<Float> x = new ArrayList<Float>(), y = new ArrayList<Float>();

		if(chosenFormat.equalsIgnoreCase("Ventana")) ;		//TODO: ventana format stuff
		else if(chosenFormat.equalsIgnoreCase("Aperio")) retVal = AperioInterpreter.scaleROIStoLowresImage();

		for(int i=0; i<retVal.get(0).size(); i++){
			retVal.get(0).set(i, (float) ( (retVal.get(0).get(i) * Math.abs(avgROIzoomRatioXY[0])) + tissueLocationOnSlide[0]));
			retVal.get(1).set(i, (float) ( (retVal.get(1).get(i) * Math.abs(avgROIzoomRatioXY[1])) + tissueLocationOnSlide[1]));
		}

		if(showOnImage) MiniBioformatsTool.attachROIStoImage(fullSlideImage, retVal);
		return retVal;
	}

	public ArrayList<ArrayList<Float>> recreateROIListRelativeToStartPoint(){
		return recreateROIListRelativeToStartPoint(rois, AperioInterpreter.getUserSelectedZero());
	}

	public static ArrayList<ArrayList<Float>> recreateROIListRelativeToStartPoint(ArrayList<ArrayList<Float>> vertices, double[] startPointXY){

		for(int i=0; i<vertices.get(0).size(); i++){
			vertices.get(0).set(i, (float) ( vertices.get(0).get(i) - startPointXY[0] ) );
			vertices.get(1).set(i, (float) ( vertices.get(1).get(i) - startPointXY[1] ) );
		}
		//MiniBioformatsTool.attachROIStoImage(fullSlideImage, vertices);
		return vertices;
	}
	
	private void calculateSlidePPM(){
		double[] PPM = AperioInterpreter.getPPMofLowResImage();
		PPM[0] *= Math.abs(avgROIzoomRatioXY[0]);
		PPM[1] *= Math.abs(avgROIzoomRatioXY[1]);
		PPM_SLIDE_XY = PPM;
	}

	public void writeWiscScanXYZ(){
		writeWiscScanXYZ(fullFilePath, rois);
	}

	public void writeWiscScanXYZ(String filepath, ArrayList<ArrayList<Float>> vertices){
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(filepath + "_WISCSCAN.xyz");
			writer.println("This is a WiscScan generated XYZ-position file. Modifying the contents of this file could cause WiscScan to crash while loading the	file!");
			writer.println("X\tY\tXRect\tYRect");

			for(int i=0; i<vertices.get(0).size(); i++)
				writer.println( ( vertices.get(0).get(i) * (PPM_WISCSCAN_XY[0] / PPM_SLIDE_XY[0]) )
						+ "\t" + ( vertices.get(1).get(i) * (PPM_WISCSCAN_XY[1] / PPM_SLIDE_XY[1]) )
						+ "\t" + "0");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			if(writer != null) writer.close();
		}
	}












	//		try{
	//			
	//			promptForFile();
	//			Interpreter.batchMode = true;
	//			
	//			findSIFTCorrespondences();
	//
	//			Roi thumbROIS = thumbnail[0].getRoi();
	//			Roi slideROIS = slideImages[0].getRoi();
	//			FloatPolygon thumbPolygon = thumbROIS.getFloatPolygon();
	//			FloatPolygon slidePolygon = slideROIS.getFloatPolygon();
	//
	//			//hard coding this because FIJI/ImageJ does not support reading in	imagestack with different sized images.
	//			//also swapping x and y because the LARGE pic with ROIs in Ventana needs to	be rotated 90* to match orientation of slide.
	//			//bigTissuePicSizeX = 40944; //42208;
	//			//slideImages[slideImages.length-1].getWidth();
	//			//bigTissuePicSizeY = 70224; //79728;
	//			//slideImages[slideImages.length-1].getHeight();
	//
	//			ArrayList<ArrayList<Float>> vertices =	interpretROISfromVentana(slideImagePath + ".xml");
	//			if(vertices == null)
	//				IJ.log("ERROR");
	//
	//			scaledXYStartPoint = calculateStartPoint(thumbPolygon, slidePolygon);
	//			vertices = scaleRoisToStartPoint(rotateClockwise90(vertices));
	//			writeWiscScanXYZ(vertices);
	//
	//			//thumbnail[0].deleteRoi();
	//			slideImages[0].deleteRoi();
	//
	//			//this is just for visually creating ROIs on the image
	//			int npoints = vertices.get(0).size();
	//			float[] x = new float[npoints], y = new float[npoints];
	//
	//			for(int i=0; i < npoints; i++){
	//				x[i] = vertices.get(0).get(i).floatValue();
	//				y[i] = vertices.get(1).get(i).floatValue();
	//			}
	//			slideImages[0].setRoi((new PolygonRoi(x, y, Roi.POLYGON))); //new Roi(vertices.get(0).get(i), vertices.get(1).get(i), 0, 0), true);
	//
	//
	//			IJ.log("done");
	//
	//		} catch(Exception e){
	//			IJ.log("Most likely CANCEL pressed...");
	//			IJ.log(e.getMessage());
	//			IJ.log(e.getLocalizedMessage());
	//			IJ.log(e.toString());
	//		}




	//	public void writeWiscScanXYZ(ArrayList<ArrayList<Float>> vertices){
	//		PrintWriter writer = null;
	//		try {
	//			writer = new PrintWriter(slideImagePath + "_WISCSCAN.xyz");
	//			writer.println("This is a WiscScan generated XYZ-position file. Modifying the contents of this file could cause WiscScan to crash while loading the	file!\n");
	//			writer.println("X\tY\tXRect\tYRect\n");
	//			for(int i=0; i<vertices.get(0).size(); i++)
	//				writer.println((vertices.get(0).get(i)*(slideActualMicronSizeX/slideSizeX))
	//						+ "\t" + (vertices.get(1).get(i)*(slideActualMicronSizeX/slideSizeX)) +
	//						"\t" + "0");
	//		} catch (FileNotFoundException e) {
	//			// TODO Auto-generated catch block
	//			e.printStackTrace();
	//		} finally{
	//			if(writer != null) writer.close();
	//		}
	//	}

	public static ArrayList<ArrayList<Float>> rotateClockwise90(ArrayList<ArrayList<Float>> vertices){
		//Rotating: newX = bigTissuePicSizeY - vertices.get(1).get(i) === newX = sizeOfBigPicture_Y - ROI_Y
		//          newY = vertices.get(0).get(i) {before changes made, so temp} === newY = X

		float temp;
		for(int i=0; i<vertices.get(0).size(); i++){

			//set new left aligned x coords
			temp = vertices.get(0).get(i);
			vertices.get(0).set(i, (vertices.get(1).get(i)));

			//set new top aligned y coords
			vertices.get(1).set(i, temp);
		}

		return vertices;

	}

	//	public ArrayList<ArrayList<Float>> scaleRoisToStartPoint(ArrayList<ArrayList<Float>> vertices){
	//		for(int i=0; i<vertices.get(0).size(); i++){
	//			vertices.get(0).set(i, (scaledXYStartPoint[0] +	thumbSizeX*Math.abs(ratioX)) - (vertices.get(0).get(i) * ((float)(thumbSizeX) / (float)(bigTissuePicSizeX)) * Math.abs(ratioX)) );
	//			vertices.get(1).set(i, scaledXYStartPoint[1] + (vertices.get(1).get(i) * ((float)(thumbSizeY) / (float)(bigTissuePicSizeY)) * Math.abs(ratioY)) );
	//		}
	//		return vertices;
	//	}


	private float[] calculateStartPoint(FloatPolygon thumbnailPolygon, FloatPolygon slidePolygon){

		float[] xThumb = thumbnailPolygon.xpoints;
		float[] yThumb = thumbnailPolygon.ypoints;
		float[] xSlide = slidePolygon.xpoints;
		float[] ySlide = slidePolygon.ypoints;

		if(avgROIzoomRatioXY == null) avgROIzoomRatioXY = new float[2];
		avgROIzoomRatioXY[0] = 0;
		avgROIzoomRatioXY[1] = 0;

		float thumbSizeX = lowresScanImage.getWidth();


		int npoints = thumbnailPolygon.npoints-1;
		float[] xRatio = new float[npoints];
		float[] yRatio = new float[npoints];
		float[] xStartPoints = new float[npoints];
		float[] yStartPoints = new float[npoints];
		float xAverage = 0, yAverage = 0, xStd = 0, yStd = 0, xFinal = 0, yFinal = 0;
		float ratioAverageX = 0, ratioAverageY = 0, ratioStdX = 0, ratioStdY = 0;


		//calculate the ratio of a line between two points in the Slide image and Thumbnail image (gives x and y scaling)
		//then use this scaling factor to calculate the estimated start point for each.
		for(int i=0; i<npoints; i++){
			xRatio[i] = (xSlide[i+1] - xSlide[i]) / ((thumbSizeX - xThumb[i+1]) - (thumbSizeX - xThumb[i]));
			yRatio[i] = (ySlide[i+1] - ySlide[i]) / (yThumb[i+1] - yThumb[i]);
			ratioAverageX += xRatio[i];
			ratioAverageY += yRatio[i];
			xStartPoints[i] = xSlide[i] - ((thumbSizeX - xThumb[i]) * xRatio[i]);
			yStartPoints[i] = ySlide[i] - (yThumb[i] * yRatio[i]);
			xAverage += xStartPoints[i];
			yAverage += yStartPoints[i];
		}

		xAverage /= npoints;
		yAverage /= npoints;
		ratioAverageX /= npoints;
		ratioAverageY /= npoints;

		//The above calculated start point has a few outliers that throw off estimation badly, so calculate standard dev.
		for(int i=0; i<npoints; i++){
			xStd = (float) (xStd + Math.pow(xStartPoints[i] - xAverage, 2));
			yStd = (float) (yStd + Math.pow(yStartPoints[i] - yAverage, 2));

			ratioStdX = (float) (ratioStdX + Math.pow(xRatio[i] - ratioAverageX, 2));
			ratioStdY = (float) (ratioStdY + Math.pow(yRatio[i] - ratioAverageY, 2));
		}

		xStd = (float) Math.sqrt(xStd / npoints);
		yStd = (float) Math.sqrt(yStd / npoints);
		ratioStdX = (float) Math.sqrt(ratioStdX / npoints);
		ratioStdY = (float) Math.sqrt(ratioStdY / npoints);


		//and only take the average of values inside the standard deviation. Gives MUCH better estimate.
		int xCounter = 0, yCounter = 0, ratioCounterX = 0, ratioCounterY = 0;
		for(int i=0; i<npoints; i++){
			if(Math.abs(xStartPoints[i] - xAverage) < xStd){
				xFinal += xStartPoints[i];
				xCounter++;
			}
			if(Math.abs(yStartPoints[i] - yAverage) < yStd){
				yFinal += yStartPoints[i];
				yCounter++;
			}

			if(Math.abs(xRatio[i] - ratioAverageX) < ratioStdX){
				avgROIzoomRatioXY[0] += xRatio[i];
				ratioCounterX++;
			}
			if(Math.abs(yRatio[i] - ratioAverageY) < ratioStdY){
				avgROIzoomRatioXY[1] += yRatio[i];
				ratioCounterY++;
			}
		}

		avgROIzoomRatioXY[0] /= ratioCounterX;
		avgROIzoomRatioXY[1] /= ratioCounterY;

		float[] retVal = new float[2];
		retVal[0] = (xFinal/xCounter) - thumbSizeX*Math.abs(avgROIzoomRatioXY[0]);
		retVal[1] = yFinal/yCounter;

		return retVal;

	}



	//	public void promptForFile(){
	//		/*
	//		 * Prompt for path of .tif file with same name as thumbnail and xml ROIs
	//		 */
	//		ImporterOptions options = null;
	//		ImagePlusReader reader = null;
	//		ImportProcess process = null;
	//		try{
	//			options = new ImporterOptions();
	//			options.loadOptions();
	//			options.parseArg(null);
	//			options.checkObsoleteOptions();
	//			//options = parseOptions(null);
	//
	//			process = new ImportProcess(options);
	//			new ImporterPrompter(process);
	//			process.execute();
	//			//showDialogs(process);
	//
	//			//DisplayHandler displayHandler = new DisplayHandler(process);
	//			//displayHandler.displayOriginalMetadata();
	//			//displayHandler.displayOMEXML();
	//			reader = new ImagePlusReader(process);
	//			slideImages = reader.openImagePlus();
	//			
	//			slideImagePath = process.getImageReader().getCurrentFile();
	//			process.getReader().close();
	//
	//			slideImages[0].show();
	//
	//			slideSizeX = slideImages[0].getWidth();
	//			slideSizeY = slideImages[0].getHeight();
	//			slideImagePath = slideImagePath.substring(0, slideImagePath.lastIndexOf('.'));
	//
	//			/*
	//			 * Load the thumbnail of tissue of on same slide
	//			 */
	//
	//			options = null;
	//			options = new ImporterOptions();
	//			options.setId(slideImagePath + "_thumb.bmp");
	//			options.setQuiet(true);
	//			options.setWindowless(true);
	//
	//			process = null;
	//			process = new ImportProcess(options);
	//			process.execute();
	//
	//			reader = null;
	//			reader = new ImagePlusReader(process);
	//			thumbnail = reader.openImagePlus();
	//			//slideImages[1] = reader.openImagePlus()[0];
	//			process.getReader().close();
	//
	//			IJ.run(thumbnail[0], "Rotate 90 Degrees Right", "");
	//			thumbnail[0].show();
	//
	//			thumbSizeX = thumbnail[0].getWidth();
	//			thumbSizeY = thumbnail[0].getHeight();
	//
	//		} catch(Exception e){
	//			IJ.log(e.getMessage());
	//			IJ.log(e.getLocalizedMessage());
	//			IJ.log(e.toString());
	//		}
	//
	//	}

	public String removeNullFromString(String line){
		StringBuilder sb = new StringBuilder(line);
		char[] temp = line.toCharArray();
		int counter = 0;
		for(int c = 0; c < temp.length; c++){
			if(temp[c] == '\0'){
				sb.deleteCharAt(c-counter);
				counter++;
			}
		}
		return sb.toString();
	}

	public ArrayList<ArrayList<Float>> interpretROISfromVentana(String
			filename){

		ArrayList<ArrayList<Float>> retVal = null;
		ArrayList<Float> x = null, y = null;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(filename));
			String line;
			retVal = new ArrayList<ArrayList<Float>>();
			x = new ArrayList<Float>();
			y = new ArrayList<Float>();

			int indexTracker = 0, nextIndexTracker=0;
			while( (line = reader.readLine()) != null){
				line = removeNullFromString(line);

				while(indexTracker >= 0){
					indexTracker = line.indexOf("X=\"", indexTracker);
					nextIndexTracker = line.indexOf("\"", indexTracker+3);
					if(indexTracker < 0 || nextIndexTracker < 0){
						indexTracker = nextIndexTracker = 0;
						break;
					}

					x.add( Float.parseFloat( line.substring( indexTracker + 3, nextIndexTracker	) ) );

					indexTracker = line.indexOf("Y=\"", indexTracker);
					nextIndexTracker = line.indexOf("\"", indexTracker+3);

					y.add( Float.parseFloat( line.substring( indexTracker + 3, nextIndexTracker	) ) );
				}

			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			retVal = null;
			x = null;
			y = null;
		}

		try {
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if(retVal != null && x != null && y!= null && x.size() > 0 && y.size() > 0){
			retVal.add(x);
			retVal.add(y);
		}
		return retVal;
	}
}
