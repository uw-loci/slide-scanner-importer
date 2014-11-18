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
import ij.gui.PolygonRoi;
import ij.gui.Roi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import loci.common.xml.XMLTools;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import ome.xml.meta.MetadataStore;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.meta.OMEXMLMetadataRoot;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.w3c.dom.Element;

public class MiniBioformatsTool {

	final String imageID;
	ImporterOptions options;
	ImportProcess process;
	ImagePlus[] imps;
	OMEXMLMetadata omexmlMeta;
	MetadataStore store;
	OMEXMLMetadataRoot root;
	String tiffInfo;

	public MiniBioformatsTool(String fullImagePath) throws Exception{

		imageID = fullImagePath;

		// initialize Bio-Formats Importer
		options = new ImporterOptions();
		options.setId(fullImagePath);
		process = new ImportProcess(options);

		if (!process.execute()) throw new IllegalStateException("Process failed");

		store = process.getReader().getMetadataStore();
		omexmlMeta = (OMEXMLMetadata) store;
		root = (OMEXMLMetadataRoot) omexmlMeta.getRoot();
		populateTiffInfo();

		IJ.log("");
	}
	
	public ImageProcessorReader getReader(){
		return process.getReader();
	}
	
	public ImportProcess getProcess(){
		return process;
	}
	
	public ImporterOptions getOptions(){
		return options;
	}

	public String getTiffInfo(){
		return tiffInfo;
	}

	private void populateTiffInfo(){
		try {
			tiffInfo = TIFFINFO(imageID);
		} catch (Throwable e){
			IJ.log("Tiff Info Cannot Be Populated");
			return;
		}		
	}

	public static String TIFFINFO(String path) throws ImageReadException, IOException{
		File img3 = new File(path);
		return Imaging.dumpImageFile(img3);

		//		String temp = Imaging.getXmpXml(img3);
		//		ICC_Profile temp3 = Imaging.getICCProfile(img3);
		//		Dimension temmp4 = Imaging.getImageSize(img3);
		//		IImageMetadata imeta = Imaging.getMetadata(img3);	//this has what we want, but difficult to get
		//		List<? extends IImageMetadataItem> metaItems = imeta.getItems();
	}

	public static void attachROIStoImage(ImagePlus imp, ArrayList<ArrayList<Float>> vertices ){

		imp.deleteRoi();

		//this is just for visually creating ROIs on the image, assuming vertices is list of <x,y> coordinates and there are equal 
		//	number of x and y points (if there aren't, then vertices weren't generated properly)
		int npoints = vertices.get(0).size();
		float[] x = new float[npoints], y = new float[npoints];

		for(int i=0; i < npoints; i++){
			x[i] = vertices.get(0).get(i).floatValue();
			y[i] = vertices.get(1).get(i).floatValue();
		}
		imp.setRoi((new PolygonRoi(x, y, Roi.POLYGON))); 
		imp.show();
	}

	public int[] extractDimensionsFromBFReader(){
		return extractDimensionsFromBFReader(0);
	}

	public int[] extractDimensionsFromBFReader(int imageIndex){

		// extract 0th series sizeX, sizeY from Bio-Formats reader
		ImageProcessorReader r = process.getReader();
		r.setSeries(imageIndex); // query the series at index

		int dimensionsXY[] = new int[2];
		dimensionsXY[0] = r.getSizeX();
		dimensionsXY[1] = r.getSizeY();
		return dimensionsXY;

	}

	public int[] extractDimensionsFromOMEXML(){
		return extractDimensionsFromOMEXML(0);
	}

	public int[] extractDimensionsFromOMEXML(int imageIndex){

		// extract OME-XML
		if (!(store instanceof OMEXMLMetadata)) {
			throw new IllegalStateException("Not OME-XML");
		}

		int dimensionsXY[] = new int[2];
		dimensionsXY[0] = root.getImage(imageIndex).getPixels().getSizeX().getValue();
		dimensionsXY[1] = root.getImage(imageIndex).getPixels().getSizeY().getValue();
		return dimensionsXY;

	}

	public Element extractXMLasDOM(){

		// let's have the OME-XML as a DOM (untested!)
		//final Element rootElement = root.asXMLElement(XMLTools.createDocument());
		return root.asXMLElement(XMLTools.createDocument());

	}

	public String extractXMLasString(){
		// let's have all the OME-XML as a string
		//final String xml = omexmlMeta.dumpXML();
		return omexmlMeta.dumpXML();
	}

	public void close() throws IOException{
		// needed to close open files
		if (!options.isVirtual()) process.getReader().close();
	}

	protected void finalize() throws Throwable{
		close();
		for(int i=0; i<imps.length; i++) imps[i].close();

	}
}


