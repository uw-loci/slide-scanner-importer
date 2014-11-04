package loci.apps.SlideScannerImport;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import org.w3c.dom.Element;

import ome.xml.meta.MetadataStore;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.meta.OMEXMLMetadataRoot;
import loci.common.xml.XMLTools;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImagePlusReader;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;

public class AperioScannerInterpreter {
	private String svsFullPath, xmlFullPath;
	private ImagePlus fullSlideImage, lowresScanImage;
	private ArrayList<ArrayList<Float>> rois;
	private double[] zeroPoint, zoomRatioXY;
	private int[] largeImageDimensions;
	private MiniBioformatsTool xmlHolder;

	public AperioScannerInterpreter(String svsFullPath, String xmlFullPath) {

		this.svsFullPath = svsFullPath;
		this.xmlFullPath = xmlFullPath;

		try{
			xmlHolder = new MiniBioformatsTool(svsFullPath);
			largeImageDimensions = xmlHolder.extractDimensionsFromOMEXML(0);
			xmlHolder.close();
			xmlHolder = null;
			
			ImagePlus slideImages[] = openAperioImages(svsFullPath);
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
			IJ.log("blah");

		} catch(Exception e){
			IJ.log(e.getMessage());
			IJ.log(e.getLocalizedMessage());
			IJ.log(e.toString());
		}		
	}
	
	public ImagePlus getFullSlideImage(){
		return fullSlideImage;
	}
	
	public ImagePlus getLowResScanImage(){
		return lowresScanImage;
	}
	
	public double getSlidePixelsPerMicron(){
		
		return 0;
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
	
	public static ImagePlus[] openAperioImages (String path) throws IOException, FormatException{
		ImporterOptions options = new ImporterOptions();
		options.setId(path);
		options.setAutoscale(false);
		options.setSplitChannels(false);
		options.setSplitTimepoints(false);
		options.setSplitFocalPlanes(false);
		options.setShowMetadata(false);
		options.setShowOMEXML(false);

		//This is really counting on the fact that Aperio images always have 7 images, where the last one is the full slide image.
		//	If this ever changes, then this code is wrong, and we'd need the user to identify which image is which.
		options.setSeriesOn(0, false);
		options.setSeriesOn(1, false);
		options.setSeriesOn(2, false);
		options.setSeriesOn(3, true);
		options.setSeriesOn(4, false);
		options.setSeriesOn(5, false);
		options.setSeriesOn(6, true);

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


// open=[D:\\LOCI\\Aperio Images\\test images\\Stroma Pancreas 2000.svs] color_mode=Default open_files display_metadata display_ome-xml specify_range view=Hyperstack stack_order=XYCZT series_4 series_7 c_begin_4=1 c_end_4=3 c_step_4=1 c_begin_7=1 c_end_7=3 c_step_7=1"