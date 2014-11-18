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
import ij.ImagePlus;
import ij.gui.GenericDialog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;

public class AperioScannerInterpreter {
	private String svsFullPath, xmlFullPath;
	private ImagePlus fullSlideImage, lowresScanImage;
	private ArrayList<ArrayList<Float>> rois;
	private double[] zeroPoint, zoomRatioXY;
	private int[] largeImageDimensions;
	private MiniBioformatsTool xmlHolder;
	private String XMLasString;

	public AperioScannerInterpreter(String imageFullPath, String xmlFullPath) {

		this.svsFullPath = imageFullPath;
		this.xmlFullPath = xmlFullPath;

		try{
			xmlHolder = new MiniBioformatsTool(svsFullPath);
			largeImageDimensions = xmlHolder.extractDimensionsFromOMEXML(0);
			XMLasString = xmlHolder.extractXMLasString();
			xmlHolder.close();
			xmlHolder = null;
			
			ImagePlus slideImages[] = openAperioImages(svsFullPath, getNeededImageIndexes()); 
			fullSlideImage = slideImages[1];
			lowresScanImage = slideImages[0];
			//			Debug:
			//			slideImages[0].show();
			//			slideImages[1].show();

			rois = interpretROISfromXML();
			//			Debug:
			//			attachROIStoImage(lowresScanImage, rois);
			zeroPoint = promptForStartPoint(fullSlideImage);
			zoomRatioXY = new double[2];
			zoomRatioXY[0] = largeImageDimensions[0] / lowresScanImage.getWidth();
			zoomRatioXY[1] = largeImageDimensions[1] / lowresScanImage.getHeight();
			
			slideImages[0].close();
			slideImages[1].close();


		} catch(Exception e){
			IJ.log(e.getMessage());
			IJ.log(e.getLocalizedMessage());
			IJ.log(e.toString());
		}		
	}
	
	public boolean[] getNeededImageIndexes(){
		
		int indexTracker = 0, nextIndexTracker = 0;
		
		indexTracker = XMLasString.lastIndexOf("Image:") + "Image:".length();
		nextIndexTracker = XMLasString.indexOf("\"", indexTracker);
		int numImages = Integer.parseInt( XMLasString.substring( indexTracker, nextIndexTracker) ) +1;
		
		boolean[] retVal = new boolean[numImages];
		retVal[numImages-1] = true;
		retVal[numImages-4] = true;
		
		return retVal;
	}
	
	public ImagePlus getFullSlideImage(){
		return fullSlideImage;
	}
	
	public ImagePlus getLowResScanImage(){
		return lowresScanImage;
	}
	
	public ArrayList<ArrayList<Float>> scaleROIStoLowresImage(){
		
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
	
	public static ImagePlus[] openAperioImages (String path, boolean[] seriesToOpen) throws IOException, FormatException{
		ImporterOptions options = new ImporterOptions();
		options.setId(path);
		options.setAutoscale(false);
		options.setSplitChannels(false);
		options.setSplitTimepoints(false);
		options.setSplitFocalPlanes(false);
		options.setShowMetadata(false);
		options.setShowOMEXML(false);
		
		for(int i=0; i<seriesToOpen.length; i++){
			options.setSeriesOn(i, seriesToOpen[i]);
		}

		return BF.openImagePlus(options);
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
			while( (line = reader.readLine()) != null){

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


}
