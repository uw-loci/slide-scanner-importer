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
import ij.process.FloatPolygon;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class VentanaScannerInterpreter { //extends Import_Slide_Scanner_ROIs {
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
	
	//
	//This method is necessary for Ventana files. They add a null character after EVERY character in their XML.
	//
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
