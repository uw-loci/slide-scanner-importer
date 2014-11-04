package loci.apps.SlideScannerImport;

import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;

import java.io.IOException;
import java.util.ArrayList;

import loci.common.xml.XMLTools;
import loci.formats.FormatException;
import loci.plugins.in.ImagePlusReader;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import ome.xml.meta.MetadataStore;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.meta.OMEXMLMetadataRoot;

import org.w3c.dom.Element;

public class MiniBioformatsTool {

	final String imageID;
	ImporterOptions options;
	ImportProcess process;
	//	ImagePlusReader reader;
	ImagePlus[] imps;
	OMEXMLMetadata omexmlMeta;
	MetadataStore store;
	OMEXMLMetadataRoot root;

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

	}

	public ImagePlus[] getImages() throws FormatException, IOException{
		if(imps == null){
			ImagePlusReader reader = new ImagePlusReader(process);
			imps = reader.openImagePlus();
		}
		return imps;
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


