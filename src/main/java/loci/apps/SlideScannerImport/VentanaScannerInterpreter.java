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

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImagePlusReader;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import loci.plugins.in.ImporterPrompter;
import loci.plugins.util.ImageProcessorReader;
import ij.process.FloatPolygon;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;











import java.util.List;

import ome.xml.meta.MetadataStore;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.IImageMetadata;
import org.apache.commons.imaging.common.IImageMetadata.IImageMetadataItem;
import org.w3c.dom.Element;

public class VentanaScannerInterpreter { 

	private String tifFullPath, thumbnailFullPath, xmlFullPath;
	private ImagePlus fullSlideImage, lowresScanImage;
	private ArrayList<ArrayList<Float>> rois;
	private double[] zeroPoint, zoomRatioXY;
	private int[] largeImageDimensions;
	private MiniBioformatsTool xmlHolder;
	private String XMLasString;

	public VentanaScannerInterpreter(String imageFullPath, String thumbFullPath, String xmlFullPath) {

		this.tifFullPath = imageFullPath;
		this.xmlFullPath = xmlFullPath;
		this.thumbnailFullPath = thumbFullPath;

		try{
			largeImageDimensions = new int[2];
			xmlHolder = new MiniBioformatsTool(tifFullPath);
			getLargestImageDimensions();

			//for ventana format, first image is slide image, second thru eleventh is largest to smallest.
			// we need to open a medium sized image for SIFT extraction
			boolean[] temp = {true};
			openVentanaImages();
			//			Debug:
//			fullSlideImage.show();
//			lowresScanImage.show();

			rois = interpretROISfromXML();		
			
			zeroPoint = promptForStartPoint(fullSlideImage);
			zoomRatioXY = new double[2];
			zoomRatioXY[0] = largeImageDimensions[0] / lowresScanImage.getWidth();
			zoomRatioXY[1] = largeImageDimensions[1] / lowresScanImage.getHeight();
			
			//			Debug:
//			rois = scaleROIStoLowresImage();
//			MiniBioformatsTool.attachROIStoImage(lowresScanImage, rois);
			IJ.log("");

		} catch(Exception e){
			IJ.log(e.getMessage());
			IJ.log(e.getLocalizedMessage());
			IJ.log(e.toString());
		}		
	}

	private void getLargestImageDimensions(){

		try{
			//Inefficient, but works:

			XMLasString = xmlHolder.getTiffInfo();

			int indexTracker = 0, nextIndexTracker=0;
			final String imgWidthKey = "2: 256 (0x100: ImageWidth): ";
			final String imgLengthKey = "2: 257 (0x101: ImageLength): ";
			final String dimensionType = " (z xxxxy)";	//Z is number of types (almost always 1), xxxxy is "long" or "short"

			indexTracker = XMLasString.indexOf(imgWidthKey, indexTracker) + imgWidthKey.length();		//first get to LARGE image (always image 2)
			nextIndexTracker = XMLasString.indexOf(" (", indexTracker);
			String imgWidth = XMLasString.substring(indexTracker, nextIndexTracker);

			String widthType = XMLasString.substring(nextIndexTracker+1, nextIndexTracker+dimensionType.length());

			indexTracker = XMLasString.indexOf(imgLengthKey, indexTracker) + imgLengthKey.length();		//first get to LARGE image (always image 2)
			nextIndexTracker = XMLasString.indexOf(" (", indexTracker);
			String imgLength = XMLasString.substring(indexTracker, nextIndexTracker);

			String lengthType = XMLasString.substring(nextIndexTracker+1, nextIndexTracker+dimensionType.length());

			largeImageDimensions[0] = parseType(widthType, Integer.parseInt(imgWidth));
			largeImageDimensions[1] = parseType(lengthType, Integer.parseInt(imgLength));

			IJ.log("Largest Image Dimensions: " + largeImageDimensions[0] + "x" + largeImageDimensions[1]);
		} catch(Throwable e){
			IJ.log(e.getMessage());
		}

	}

	private int parseType(String type, int value){
		if(value<0){
			type = type.toLowerCase();
			if(type.contains("short")){
				return (int) (2*Short.MAX_VALUE + value + 2);
			}
			if(type.contains("long")){
				return (int) (2*Long.MAX_VALUE + value);
			}
		}
		return value;
	}

	public ImagePlus getFullSlideImage(){
		return fullSlideImage;
	}

	public ImagePlus getLowResScanImage(){
		return lowresScanImage;
	}

	public ArrayList<ArrayList<Float>> scaleROIStoLowresImage(){
		return scaleROIStoLowresImage(rois, zoomRatioXY);
	}
	
	public static ArrayList<ArrayList<Float>> scaleROIStoLowresImage(ArrayList<ArrayList<Float>> rois, double[] zoomRatioXY){

		ArrayList<ArrayList<Float>> retVal = new ArrayList<ArrayList<Float>>();
		ArrayList<Float> x = new ArrayList<Float>(), y = new ArrayList<Float>();

		for(int i=0; i<rois.get(0).size(); i++){
			x.add( (float) (rois.get(0).get(i) / zoomRatioXY[0]));
			y.add( (float) (rois.get(1).get(i) / zoomRatioXY[1]));
		}
		//MiniBioformatsTool.attachROIStoImage(lowresScanImage, retVal);
		retVal.add(x);
		retVal.add(y);
		return retVal;
	}

	private void openVentanaImages (){

		try{
			ImporterOptions options = xmlHolder.getOptions();
			options.setSpecifyRanges(true);
			options.setTBegin(0, 0);
			options.setTEnd(0, 0);
			ImportProcess process = new ImportProcess(options);
			if (!process.execute()) throw new IllegalStateException("Process failed");
			
			ImagePlusReader reader = new ImagePlusReader(process);
			fullSlideImage = reader.openImagePlus()[0];
			IJ.run(fullSlideImage, "Rotate 90 Degrees Left", "");

			MiniBioformatsTool thumbnailHolder = new MiniBioformatsTool(thumbnailFullPath);
			reader = new ImagePlusReader(thumbnailHolder.getProcess());
			lowresScanImage = reader.openImagePlus()[0];

			thumbnailHolder.close();			

		} catch (Throwable e){
			IJ.log(e.toString());
		}
	}

	public double[] getUserSelectedZero(){
		return zeroPoint;
	}

	private static double[] getUserEnteredStartPoint(ImagePlus imp){

		imp.show();

		MouseClickListener mouse = new MouseClickListener(imp);
		while(!mouse.getMouseClicked()){
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		imp.close();
		return mouse.getClickCoordinates();
	}

	private static double[] promptForStartPoint(ImagePlus imp){

		GenericDialog gd2 = new GenericDialog("Select a start point");
		gd2.addMessage("The slide image will now load. On the image, select ONE point as your \"ZERO\" or \"START\" point.\n"
				+ "All ROI coordinates will be created relative to this point for WiscScan.\n"
				+ "\nIn WiscScan, you will need to navigate to this point manually using microscope controls, and press SET ZERO in the XY Motor page.\n"
				+ "If you're using an inverted scope, check the INVERTED checkbox below.");
		gd2.addCheckbox("Inverted", false);
		gd2.showDialog();

		if(gd2.wasCanceled()) return null;
		return getUserEnteredStartPoint(imp.duplicate());

	}

	public double[] getPPMofLowResImage(){
		String fullXML = XMLasString;
		double[] retVal = new double[2];
		int indexTracker = 0, nextIndexTracker = 0;

		indexTracker = fullXML.indexOf("PhysicalSizeX=\"") + "PhysicalSizeX\"".length() +1;
		nextIndexTracker = fullXML.indexOf("\"", indexTracker);
		retVal[0] = 1 / Double.parseDouble( fullXML.substring( indexTracker, nextIndexTracker) );

		indexTracker = fullXML.indexOf("PhysicalSizeY=\"") + "PhysicalSizeY\"".length() +1;
		nextIndexTracker = fullXML.indexOf("\"", indexTracker);
		retVal[1] = 1 / Double.parseDouble( fullXML.substring( indexTracker, nextIndexTracker) );

		retVal[0] /= zoomRatioXY[0];
		retVal[1] /= zoomRatioXY[1];

		return retVal;
	}

	public double[] getZoomRatioXY(){
		return zoomRatioXY;
	}

	public ArrayList<ArrayList<Float>> getROIsList(){
		return rois;
	}

	public ArrayList<ArrayList<Float>> interpretROISfromXML(){
		return interpretROISfromXML(xmlFullPath);
	}

	public static ArrayList<ArrayList<Float>> interpretROISfromXML(String xmlPath){

		ArrayList<ArrayList<Float>> retVal = null;
		ArrayList<Float> x = null, y = null;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(xmlPath));
			String line;
			retVal = new ArrayList<ArrayList<Float>>();
			x = new ArrayList<Float>();
			y = new ArrayList<Float>();

			int indexTracker = 0, nextIndexTracker=0;
			while( (line = removeNullFromString(reader.readLine())) != null){

				
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

				} //end while(indexTracker)

			}//end while(line)

		} catch (Exception e) {
			e.printStackTrace();
			IJ.log("Error encountered while parsing XML file.");
			IJ.log(e.getStackTrace().toString());
			retVal = null;
			x = null;
			y = null;

		}//end try/catch XML parser

		try {
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			IJ.log("Error encountered while parsing XML file.");
			IJ.log(e.getStackTrace().toString());
		}

		if(retVal != null && x != null && y!= null && x.size() > 0 && y.size() > 0){
			retVal.add(x);
			retVal.add(y);
		}

		return retVal;
	}

	//
	//This method is necessary for Ventana files. They add a null character after EVERY character in their XML.
	//
	public static String removeNullFromString(String line){
		if(line == null) return null;
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



	/*

	String tiffPath, xmlPath;
	double originalScanSizeX, originalScanSizeY;

	public VentanaScannerInterpreter(String tiffFullPath, String xmlFullPath) {
		this.tiffPath=tiffFullPath;
		this.xmlPath=xmlFullPath;
	}

	public ArrayList<ArrayList<Float>> interpretROISfromXML(){
		return interpretROISfromXML(xmlPath);
	}

	public ArrayList<ArrayList<Float>> interpretROISfromXML(String xmlFullPath){

		ArrayList<ArrayList<Float>> retVal = null;
		ArrayList<Float> x = null, y = null;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(xmlFullPath));
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

				} //end while(indexTracker)

			}//end while(line)

		} catch (Exception e) {
			e.printStackTrace();
			IJ.log("Error encountered while parsing XML file.");
			IJ.log(e.getStackTrace().toString());
			retVal = null;
			x = null;
			y = null;

		}//end try/catch XML parser

		try {
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			IJ.log("Error encountered while parsing XML file.");
			IJ.log(e.getStackTrace().toString());
		}

		if(retVal != null && x != null && y!= null && x.size() > 0 && y.size() > 0){
			retVal.add(x);
			retVal.add(y);
		}
		return retVal;
	}

	
/*
	public void parseFile(){

		ImporterOptions options = null;
		ImagePlusReader reader = null;
		ImportProcess process = null;
		try{
			options = new ImporterOptions();
			options.loadOptions();
			options.parseArg(null);
			options.checkObsoleteOptions();
			//options = parseOptions(null);

			process = new ImportProcess(options);
			new ImporterPrompter(process);
			process.execute();
			//showDialogs(process);

			//DisplayHandler displayHandler = new DisplayHandler(process);
			//displayHandler.displayOriginalMetadata();
			//displayHandler.displayOMEXML();
			reader = new ImagePlusReader(process);
			slideImages = reader.openImagePlus();

			slideImagePath = process.getImageReader().getCurrentFile();
			process.getReader().close();

			slideImages[0].show();

			slideSizeX = slideImages[0].getWidth();
			slideSizeY = slideImages[0].getHeight();
			slideImagePath = slideImagePath.substring(0, slideImagePath.lastIndexOf('.'));

			/*
	 * Load the thumbnail of tissue of on same slide
	 *

			options = null;
			options = new ImporterOptions();
			options.setId(slideImagePath + "_thumb.bmp");
			options.setQuiet(true);
			options.setWindowless(true);

			process = null;
			process = new ImportProcess(options);
			process.execute();

			reader = null;
			reader = new ImagePlusReader(process);
			thumbnail = reader.openImagePlus();
			//slideImages[1] = reader.openImagePlus()[0];
			process.getReader().close();

			IJ.run(thumbnail[0], "Rotate 90 Degrees Right", "");
			thumbnail[0].show();

			thumbSizeX = thumbnail[0].getWidth();
			thumbSizeY = thumbnail[0].getHeight();

		} catch(Exception e){
			IJ.log(e.getMessage());
			IJ.log(e.getLocalizedMessage());
			IJ.log(e.toString());
		}

	}

	private float[] calculateStartPoint(FloatPolygon thumbPolygon, FloatPolygon	slidePolygon){
		float[] xThumb = thumbPolygon.xpoints;
		float[] yThumb = thumbPolygon.ypoints;
		float[] xSlide = slidePolygon.xpoints;
		float[] ySlide = slidePolygon.ypoints;

		int npoints = thumbPolygon.npoints-1;
		float[] xRatio = new float[npoints];
		float[] yRatio = new float[npoints];
		float[] xStartPoints = new float[npoints];
		float[] yStartPoints = new float[npoints];
		float xAverage = 0, yAverage = 0, xStd = 0, yStd = 0, xFinal = 0, yFinal =
				0;
		float ratioAverageX = 0, ratioAverageY = 0, ratioStdX = 0, ratioStdY = 0;
		ratioX = 0;
		ratioY = 0;

		//calculate the ratio of a line between two points in the Slide image and Thumbnail image (gives x and y scaling)
		//then use this scaling factor to calculate the estimated start point for each.
		for(int i=0; i<npoints; i++){
			xRatio[i] = (xSlide[i+1] - xSlide[i]) / ((thumbSizeX - xThumb[i+1]) -
					(thumbSizeX - xThumb[i]));
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
				ratioX += xRatio[i];
				ratioCounterX++;
			}
			if(Math.abs(yRatio[i] - ratioAverageY) < ratioStdY){
				ratioY += yRatio[i];
				ratioCounterY++;
			}
		}

		ratioX /= ratioCounterX;
		ratioY /= ratioCounterY;

		float[] retVal = new float[2];
		retVal[0] = (xFinal/xCounter) - thumbSizeX*Math.abs(ratioX);
		retVal[1] = yFinal/yCounter;

		return retVal;
	}

	 */
}
