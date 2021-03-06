package org.esa.beam.synergy.util;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.synergy.util.math.Spline;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.Interpolation;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ScaleDescriptor;
import java.awt.Color;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * Utility class for aerosol/SDR retrieval
 *
 * @author Olaf Danne
 * @version $Revision: 8041 $ $Date: 2010-01-20 16:23:15 +0000 (Mi, 20 Jan 2010) $
 */
public class AerosolHelpers {

    private static AerosolHelpers instance;

    public static AerosolHelpers getInstance() {
        if (instance == null) {
            instance = new AerosolHelpers();
        }

        return instance;
    }

    /**
     * @param inputProduct
     * @param instr
     * @param bandList
     */
    public static void getGeometryBandList(Product inputProduct, String instr, ArrayList<RasterDataNode> bandList) {
        final String[] viewArr = {"nadir", "fward"};
        int nView = viewArr.length;
        final String[] bodyArr = {"sun", "view"};
        final String[] angArr = {"elev", "azimuth"};
        String bandName;

        if (instr.equals("MERIS")) {
            angArr[0] = "zenith";
            nView = 1;
        }
        for (int iView = 0; iView < nView; iView++) {
            for (String body : bodyArr) {
                for (String ang : angArr) {
                    if (instr.equals("AATSR")) {
                        bandName = body + "_" + ang + "_" + viewArr[iView] + "_" +
                                   SynergyConstants.INPUT_BANDS_SUFFIX_AATSR;
                        bandList.add(inputProduct.getBand(bandName));
                    } else {
                        bandName = body + "_" + ang;
                        bandList.add(inputProduct.getRasterDataNode(bandName));
                    }
                }
            }
        }
    }

    public static void getSpectralBandList(Product inputProduct, String bandNamePrefix, String bandNameSuffix,
                                           int[] excludeBandIndices, ArrayList<Band> bandList) {

        final String[] bandNames = inputProduct.getBandNames();
        Comparator<Band> byWavelength = new WavelengthComparator();
        for (String name : bandNames) {
            if (name.startsWith(bandNamePrefix) && name.endsWith(bandNameSuffix)) {
                boolean exclude = false;
                if (excludeBandIndices != null) {
                    for (int i : excludeBandIndices) {
                        exclude = exclude || (i == inputProduct.getBand(name).getSpectralBandIndex() + 1);
                    }
                }
                if (!exclude) {
                    bandList.add(inputProduct.getBand(name));
                }
            }
        }
        Collections.sort(bandList, byWavelength);
    }

    /**
     * This method provides the min/max vector for ocean aerosol retrieval
     * * The method represents the breadboard IDL routine 'minmax'.
     *
     * @param inputArray - the input array
     * @param n          - array dimension
     *
     * @return float[]
     */
    public static float[] getMinMaxVector(float[] inputArray, int n) {
        float[] result = new float[n];

        float[] inputArrayCopy = new float[inputArray.length];
        System.arraycopy(inputArray, 0, inputArrayCopy, 0, inputArray.length);
        Arrays.sort(inputArrayCopy);

        float min = inputArrayCopy[0];
        float max = inputArrayCopy[inputArrayCopy.length - 1];

        for (int i = 0; i < n; i++) {
            result[i] = min + i * (max - min) / (n - 1);
        }

        return result;
    }

    /**
     * This method finds for each regular angstroem parameter
     * a PAIR of models which can be used for a weighted sum (interpolation).
     * The method represents the breadboard IDL routine
     * 'find_model_pairs_for_angstroem_interpolation'.
     *
     * @param angArray - array of Angstroem coefficients from aerosol model table
     * @param nAng     - number of Ang coeffs
     *
     * @return AngstroemParameters[] - the model pairs
     */
    public AngstroemParameters[] getAngstroemParameters(float[] angArray, int nAng) {
        final float[] minMaxVector = AerosolHelpers.getMinMaxVector(angArray, nAng);
        AngstroemParameters[] angstroemParameters = new AngstroemParameters[minMaxVector.length];

        for (int i = 0; i < minMaxVector.length; i++) {
            int lowerIndex = AerosolHelpers.getNearestLowerValueIndexInFloatArray(minMaxVector[i], angArray);
            angstroemParameters[i] = new AngstroemParameters();
            angstroemParameters[i].setIndexPairs(0, lowerIndex);
            int higherIndex = AerosolHelpers.getNearestHigherValueIndexInFloatArray(minMaxVector[i], angArray);
            angstroemParameters[i].setIndexPairs(1, higherIndex);
            angstroemParameters[i].setValue(minMaxVector[i]);
        }

        for (int i = 0; i < minMaxVector.length; i++) {
            float lowerAng = angArray[angstroemParameters[i].getIndexPairs()[0]];
            float higherAng = angArray[angstroemParameters[i].getIndexPairs()[1]];

            float distance = lowerAng - higherAng;
            if (Math.abs(distance) > 0.01) {
                double wgt0 = 1.0 - (lowerAng - minMaxVector[i]) / distance;
                angstroemParameters[i].setWeightPairs(0, wgt0);
                double wgt1 = 1.0 - (minMaxVector[i] - higherAng) / distance;
                angstroemParameters[i].setWeightPairs(1, wgt1);
            } else {
                angstroemParameters[i].setWeightPairs(0, 1.0);
                angstroemParameters[i].setWeightPairs(1, 0.0);
            }
        }

        return angstroemParameters;
    }

    /**
     * This method computed the index of the nearest higher value in a float array
     * compared to a given input float value
     *
     * @param x     - input value
     * @param array - the float array
     *
     * @return int
     */
    public static int getNearestHigherValueIndexInFloatArray(float x, float[] array) {
        int nearestValueIndex = -1;
        float big = Float.MAX_VALUE;

        for (int i = 0; i < array.length; i++) {
            if (x <= array[i]) {
                if (array[i] - x < big) {
                    big = array[i] - x;
                    nearestValueIndex = i;
                }
            }
        }
        if (nearestValueIndex == -1) {
            throw new OperatorException("Failed to create Angstroem model pairs!\n");
        }

        return nearestValueIndex;
    }

    /**
     * This method computed the index of the nearest lower value in a float array
     * compared to a given input float value
     *
     * @param x     - input value
     * @param array - the float array
     *
     * @return int
     */
    public static int getNearestLowerValueIndexInFloatArray(float x, float[] array) {
        int nearestValueIndex = -1;
        float big = Float.MAX_VALUE;

        for (int i = 0; i < array.length; i++) {
            if (x >= array[i]) {
                if (x - array[i] < big) {
                    big = x - array[i];
                    nearestValueIndex = i;
                }
            }
        }

        if (nearestValueIndex == -1) {
            throw new OperatorException("Failed to create Angstroem model pairs!\n");
        }

        return nearestValueIndex;
    }

    /**
     * This method spline-interpolates within a double array to upscale
     * the array to a given dimension
     *
     * @param yIn    - input array
     * @param dimOut - dimension of output array
     *
     * @return double[] - the upscaled array
     */
    public static double[] interpolateArray(double[] yIn, int dimOut) {
        double[] yOut = new double[dimOut];

        final int numIntervals = yIn.length - 1;
        final int numPointsPerInterval = dimOut / numIntervals;
        Spline splineInterpol = new Spline(yIn);
        int outIndex = 0;
        // interpolate in all intervals:
        for (int i = 0; i < numIntervals; i++) {
            yOut[outIndex++] = yIn[i];
            for (int j = 1; j < numPointsPerInterval; j++) {
                final double fraction = ((double) j) / numPointsPerInterval;
                yOut[outIndex++] = splineInterpol.fn(i, fraction);
            }
        }
        // don't forget the last points if there are some left:
        for (int i = outIndex; i < yOut.length; i++) {
            yOut[i] = yIn[yIn.length - 1];
        }

        return yOut;
    }

    /**
     * This method provides an average value of nxn pixels around center pixel
     *
     * @param inputProduct - the input product
     * @param inputTile    - tile containing the pixels
     * @param aveBlockSize - half size of nxn square
     * @param minAverages  - number of 'good' values required for average computation
     * @param iTarX        - x value
     * @param iTarY        - y value
     *
     * @return float
     */
    public static float getAvePixelFloat(Product inputProduct, Tile inputTile,
                                         int aveBlockSize, int minAverages,
                                         int iTarX, int iTarY) {

        double value = 0;
        double noDataValue = 0;
        int n = 0;

        final int minX = Math.max(0, iTarX - aveBlockSize);
        final int minY = Math.max(0, iTarY - aveBlockSize);
        final int maxX = Math.min(inputProduct.getSceneRasterWidth() - 1, iTarX + aveBlockSize);
        final int maxY = Math.min(inputProduct.getSceneRasterHeight() - 1, iTarY + aveBlockSize);

        for (int iy = minY; iy <= maxY; iy++) {
            for (int ix = minX; ix <= maxX; ix++) {
                double val = inputTile.getSampleDouble(ix, iy);
                noDataValue = inputTile.getRasterDataNode().getNoDataValue();
                boolean valid = (Double.compare(val, noDataValue) != 0);
                if (valid) {
                    n++;
                    value += val;
                }
            }
        }
        if (!(n < minAverages)) {
            value /= n;
        } else {
            value = noDataValue;
        }

        return (float) value;
    }

    /**
     * This method copies the flag bands from the synergy product to the target product
     *
     * @param synergyProduct - the Synergy product
     * @param targetProduct  - the target product
     */
    public static void copySynergyFlagBands(Product synergyProduct, Product targetProduct) {
        final Band aatsrConfidFlagNadirBand = targetProduct.addBand(SynergyConstants.CONFID_NADIR_FLAGS_AATSR,
                                                                    ProductData.TYPE_INT16);
        final Band aatsrConfidFlagFwardBand = targetProduct.addBand(SynergyConstants.CONFID_FWARD_FLAGS_AATSR,
                                                                    ProductData.TYPE_INT16);
        final Band aatsrCloudFlagNadirBand = targetProduct.addBand(SynergyConstants.CLOUD_NADIR_FLAGS_AATSR,
                                                                   ProductData.TYPE_INT16);
        final Band aatsrCloudFlagFwardBand = targetProduct.addBand(SynergyConstants.CLOUD_FWARD_FLAGS_AATSR,
                                                                   ProductData.TYPE_INT16);
        final Band merisL1FlagsBand = targetProduct.addBand(SynergyConstants.L1_FLAGS_MERIS, ProductData.TYPE_INT16);
        final Band merisCloudFlagBand = targetProduct.addBand(SynergyConstants.CLOUD_FLAG_MERIS,
                                                              ProductData.TYPE_INT16);

        final FlagCoding aatsrConfidNadirFlagCoding = synergyProduct.getFlagCodingGroup().get(
                SynergyConstants.CONFID_NADIR_FLAGS_AATSR);
        ProductUtils.copyFlagCoding(aatsrConfidNadirFlagCoding, targetProduct);
        aatsrConfidFlagNadirBand.setSampleCoding(aatsrConfidNadirFlagCoding);

        final FlagCoding aatsrConfidFwardFlagCoding = synergyProduct.getFlagCodingGroup().get(
                SynergyConstants.CONFID_FWARD_FLAGS_AATSR);
        ProductUtils.copyFlagCoding(aatsrConfidFwardFlagCoding, targetProduct);
        aatsrConfidFlagFwardBand.setSampleCoding(aatsrConfidFwardFlagCoding);

        final FlagCoding aatsrCloudNadirFlagCoding = synergyProduct.getFlagCodingGroup().get(
                SynergyConstants.CLOUD_NADIR_FLAGS_AATSR);
        ProductUtils.copyFlagCoding(aatsrCloudNadirFlagCoding, targetProduct);
        aatsrCloudFlagNadirBand.setSampleCoding(aatsrCloudNadirFlagCoding);

        final FlagCoding aatsrCloudFwardFlagCoding = synergyProduct.getFlagCodingGroup().get(
                SynergyConstants.CLOUD_FWARD_FLAGS_AATSR);
        ProductUtils.copyFlagCoding(aatsrCloudFwardFlagCoding, targetProduct);
        aatsrCloudFlagFwardBand.setSampleCoding(aatsrCloudFwardFlagCoding);

        final FlagCoding merisL1FlagsCoding = synergyProduct.getFlagCodingGroup().get(SynergyConstants.L1_FLAGS_MERIS);
        ProductUtils.copyFlagCoding(merisL1FlagsCoding, targetProduct);
        merisL1FlagsBand.setSampleCoding(merisL1FlagsCoding);

        final FlagCoding merisCloudFlagCoding = synergyProduct.getFlagCodingGroup().get(
                SynergyConstants.CLOUD_FLAG_MERIS);
        ProductUtils.copyFlagCoding(merisCloudFlagCoding, targetProduct);
        merisCloudFlagBand.setSampleCoding(merisCloudFlagCoding);
    }

    /**
     * This method copies selected tie point grids to a downscaled target product
     *
     * @param sourceProduct - the source product
     * @param targetProduct - the target product
     * @param scalingFactor - factor of downscaling
     */
    public static void copyDownscaledTiePointGrids(Product sourceProduct, Product targetProduct, float scalingFactor) {
        // Add tie point grids for sun/view zenith/azimuths. Get data from AATSR bands.
        final Band szaBand = sourceProduct.getBand("sun_elev_nadir_" + SynergyConstants.INPUT_BANDS_SUFFIX_AATSR);
        final Band saaBand = sourceProduct.getBand("sun_azimuth_nadir_" + SynergyConstants.INPUT_BANDS_SUFFIX_AATSR);
        final Band latitudeBand = sourceProduct.getBand("latitude_" + SynergyConstants.INPUT_BANDS_SUFFIX_AATSR);
        final Band longitudeBand = sourceProduct.getBand("longitude_" + SynergyConstants.INPUT_BANDS_SUFFIX_AATSR);
        final Band altitudeBand = sourceProduct.getBand("altitude_" + SynergyConstants.INPUT_BANDS_SUFFIX_AATSR);

        final Band szaDownscaledBand = downscaleBand(szaBand, scalingFactor);
        final Band saaDownscaledBand = downscaleBand(saaBand, scalingFactor);
        final Band latitudeDownscaledBand = downscaleBand(latitudeBand, scalingFactor);
        final Band longitudeDownscaledBand = downscaleBand(longitudeBand, scalingFactor);
        final Band altitudeDownscaledBand = downscaleBand(altitudeBand, scalingFactor);

        addTpg(targetProduct, szaDownscaledBand, "sun_zenith");
        addTpg(targetProduct, saaDownscaledBand, "sun_azimuth");
        // unscaled latitude/longitude TPGs were added by 'copyGeoCoding', we have to remove them before downscaling
        targetProduct.removeTiePointGrid(targetProduct.getTiePointGrid("latitude"));
        targetProduct.removeTiePointGrid(targetProduct.getTiePointGrid("longitude"));
        addTpg(targetProduct, latitudeDownscaledBand, "latitude");
        addTpg(targetProduct, longitudeDownscaledBand, "longitude");
        addTpg(targetProduct, altitudeDownscaledBand, "altitude");
    }

    /**
     * This method copies selected tie point grids to a rescaled target product
     *
     * @param sourceProduct  - the source product
     * @param targetProduct  - the target product
     * @param xScalingFactor - scaling factor in x-direction
     * @param yScalingFactor - scaling factor in y-direction
     */
    public static void copyRescaledTiePointGrids(Product sourceProduct, Product targetProduct,
                                                 int xScalingFactor, int yScalingFactor) {
        // Add tie point grids for sun/view zenith/azimuths. Get data from AATSR bands.
        final Band szaBand = sourceProduct.getBand("sun_elev_nadir_" + SynergyConstants.INPUT_BANDS_SUFFIX_AATSR);
        final Band saaBand = sourceProduct.getBand("sun_azimuth_nadir_" + SynergyConstants.INPUT_BANDS_SUFFIX_AATSR);
        final Band latitudeBand = sourceProduct.getBand("latitude_" + SynergyConstants.INPUT_BANDS_SUFFIX_AATSR);
        final Band longitudeBand = sourceProduct.getBand("longitude_" + SynergyConstants.INPUT_BANDS_SUFFIX_AATSR);
        final Band altitudeBand = sourceProduct.getBand("altitude_" + SynergyConstants.INPUT_BANDS_SUFFIX_AATSR);

        final TiePointGrid szaTpg = getRescaledTpgFromBand(szaBand, xScalingFactor, yScalingFactor);
        targetProduct.addTiePointGrid(szaTpg);
        final TiePointGrid saaTpg = getRescaledTpgFromBand(saaBand, xScalingFactor, yScalingFactor);
        targetProduct.addTiePointGrid(saaTpg);
        final TiePointGrid latTpg = getRescaledTpgFromBand(latitudeBand, xScalingFactor, yScalingFactor);
        targetProduct.addTiePointGrid(latTpg);
        final TiePointGrid lonTpg = getRescaledTpgFromBand(longitudeBand, xScalingFactor, yScalingFactor);
        targetProduct.addTiePointGrid(lonTpg);
        final TiePointGrid altTpg = getRescaledTpgFromBand(altitudeBand, xScalingFactor, yScalingFactor);
        targetProduct.addTiePointGrid(altTpg);

    }

    private static void addTpg(Product targetProduct, Band scaledBand, String name) {
        DataBuffer dataBuffer;
        float[] tpgData;
        TiePointGrid tpg;
        dataBuffer = scaledBand.getSourceImage().getData().getDataBuffer();
        tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }
        tpg = new TiePointGrid(name,
                               scaledBand.getSceneRasterWidth(),
                               scaledBand.getSceneRasterHeight(),
                               0.0f, 0.0f, 1.0f, 1.0f, tpgData);
        targetProduct.addTiePointGrid(tpg);
    }

    /**
     * This method provides a real tie point grid from a 'tie point band'.
     *
     * @param band - the 'tie point band'
     *
     * @return TiePointGrid
     */
    public static TiePointGrid getTpgFromBand(Band band) {
        final DataBuffer dataBuffer = band.getSourceImage().getData().getDataBuffer();
        float[] tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }

        return new TiePointGrid(band.getName(),
                                band.getSceneRasterWidth(),
                                band.getSceneRasterHeight(),
                                0.0f, 0.0f, 1.0f, 1.0f, tpgData);
    }

    /**
     * This method provides a rescaled tie point grid from a 'tie point band'.
     *
     * @param band           - the 'tie point band'
     * @param rescaledWidth  - width of the rescaled TPG
     * @param rescaledHeight - height of the rescaled TPG
     *
     * @return TiePointGrid
     */
    public static TiePointGrid getRescaledTpgFromBand(Band band, int rescaledWidth, int rescaledHeight) {
        final DataBuffer dataBuffer = band.getSourceImage().getData().getDataBuffer();
        float[] tpgData = new float[rescaledWidth * rescaledHeight];
        if (rescaledWidth * rescaledHeight > band.getSceneRasterWidth() * band.getSceneRasterHeight()) {
            throw new OperatorException("Cannot create TPG - width*height too large.");
        }
        int tpgIndex = 0;
        for (int j = 0; j < rescaledHeight; j++) {
            for (int i = 0; i < rescaledWidth; i++) {
                tpgData[rescaledWidth * j + i] = dataBuffer.getElemFloat(tpgIndex);
                tpgIndex++;
            }
            for (int i = rescaledWidth; i < band.getSceneRasterWidth(); i++) {
                tpgIndex++;
            }
        }
        return new TiePointGrid(band.getName(),
                                rescaledWidth,
                                rescaledHeight,
                                0.0f, 0.0f, 1.0f, 1.0f, tpgData);
    }

    /**
     * This method downscales a band by a given factor
     *
     * @param inputBand     - the input band
     * @param scalingFactor - the scaling factor
     *
     * @return Band - the downscaled band
     */
    public static Band downscaleBand(Band inputBand, float scalingFactor) {
        final RenderedImage sourceImage = inputBand.getSourceImage();
        final RenderedOp downscaledImage = ScaleDescriptor.create(sourceImage,
                                                                  1.0f / scalingFactor,
                                                                  1.0f / scalingFactor,
                                                                  0.0f, 0.0f,
                                                                  Interpolation.getInstance(
                                                                          Interpolation.INTERP_NEAREST),
                                                                  null);
        Band downscaledBand = new Band(inputBand.getName(), inputBand.getDataType(),
                                       downscaledImage.getWidth(), downscaledImage.getHeight());

        downscaledBand.setSourceImage(downscaledImage);
        return downscaledBand;
    }

    /**
     * This method copies all bands which contain a flagcoding from the source product
     * to the target product.
     *
     * @param sourceProduct the source product
     * @param targetProduct the target product
     */
    public static void copyDownscaledFlagBands(Product sourceProduct, Product targetProduct, float scalingFactor) {
        Guardian.assertNotNull("source", sourceProduct);
        Guardian.assertNotNull("target", targetProduct);
        if (sourceProduct.getFlagCodingGroup().getNodeCount() > 0) {

            ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
            ProductUtils.copyMasks(sourceProduct, targetProduct);

            // loop over bands and check if they have a flags coding attached
            for (int i = 0; i < sourceProduct.getNumBands(); i++) {
                Band sourceBand = sourceProduct.getBandAt(i);
                FlagCoding coding = sourceBand.getFlagCoding();
                if (coding != null) {
                    Band targetBand = AerosolHelpers.downscaleBand(sourceBand, scalingFactor);
                    targetBand.setSampleCoding(coding);
                    targetProduct.addBand(targetBand);
                }
            }
        }
    }

    public static void addAerosolFlagBand(Product targetProduct, int rasterWidth, int rasterHeight) {
        FlagCoding aerosolFlagCoding = new FlagCoding(SynergyConstants.aerosolFlagCodingName);
        aerosolFlagCoding.addFlag(SynergyConstants.flagCloudyName, SynergyConstants.cloudyMask,
                                  SynergyConstants.flagCloudyDesc);
        aerosolFlagCoding.addFlag(SynergyConstants.flagOceanName, SynergyConstants.oceanMask,
                                  SynergyConstants.flagOceanDesc);
        aerosolFlagCoding.addFlag(SynergyConstants.flagSuccessName, SynergyConstants.successMask,
                                  SynergyConstants.flagSuccessDesc);
        aerosolFlagCoding.addFlag(SynergyConstants.flagBorderName, SynergyConstants.borderMask,
                                  SynergyConstants.flagBorderDesc);
        aerosolFlagCoding.addFlag(SynergyConstants.flagFilledName, SynergyConstants.filledMask,
                                  SynergyConstants.flagFilledDesc);
        aerosolFlagCoding.addFlag(SynergyConstants.flagNegMetricName, SynergyConstants.negMetricMask,
                                  SynergyConstants.flagNegMetricDesc);
        aerosolFlagCoding.addFlag(SynergyConstants.flagAotLowName, SynergyConstants.aotLowMask,
                                  SynergyConstants.flagAotLowDesc);
        aerosolFlagCoding.addFlag(SynergyConstants.flagErrHighName, SynergyConstants.errHighMask,
                                  SynergyConstants.flagErrHighDesc);
        aerosolFlagCoding.addFlag(SynergyConstants.flagCoastName, SynergyConstants.coastMask,
                                  SynergyConstants.flagCoastDesc);
        targetProduct.getFlagCodingGroup().add(aerosolFlagCoding);
        ProductNodeGroup<Mask> maskGroup = targetProduct.getMaskGroup();
        maskGroup.add(Mask.BandMathsType.create(SynergyConstants.flagCloudyName, SynergyConstants.flagCloudyDesc,
                                                rasterWidth, rasterHeight,
                                                SynergyConstants.aerosolFlagCodingName + "." + SynergyConstants.flagCloudyName,
                                                Color.lightGray, 0.2f));
        maskGroup.add(Mask.BandMathsType.create(SynergyConstants.flagOceanName, SynergyConstants.flagOceanDesc,
                                                rasterWidth, rasterHeight,
                                                SynergyConstants.aerosolFlagCodingName + "." + SynergyConstants.flagOceanName,
                                                Color.blue, 0.2f));
        maskGroup.add(Mask.BandMathsType.create(SynergyConstants.flagSuccessName, SynergyConstants.flagSuccessDesc,
                                                rasterWidth, rasterHeight,
                                                SynergyConstants.aerosolFlagCodingName + "." + SynergyConstants.flagSuccessName,
                                                Color.pink, 0.2f));
        maskGroup.add(Mask.BandMathsType.create(SynergyConstants.flagBorderName, SynergyConstants.flagBorderDesc,
                                                rasterWidth, rasterHeight,
                                                SynergyConstants.aerosolFlagCodingName + "." + SynergyConstants.flagBorderName,
                                                Color.orange, 0.2f));
        maskGroup.add(Mask.BandMathsType.create(SynergyConstants.flagFilledName, SynergyConstants.flagFilledDesc,
                                                rasterWidth, rasterHeight,
                                                SynergyConstants.aerosolFlagCodingName + "." + SynergyConstants.flagFilledName,
                                                Color.magenta, 0.2f));
        maskGroup.add(Mask.BandMathsType.create(SynergyConstants.flagNegMetricName,
                                                SynergyConstants.flagNegMetricDesc,
                                                rasterWidth, rasterHeight,
                                                SynergyConstants.aerosolFlagCodingName + "." + SynergyConstants.flagNegMetricName,
                                                Color.magenta, 0.2f));
        maskGroup.add(Mask.BandMathsType.create(SynergyConstants.flagAotLowName,
                                                SynergyConstants.flagAotLowDesc,
                                                rasterWidth, rasterHeight,
                                                SynergyConstants.aerosolFlagCodingName + "." + SynergyConstants.flagAotLowName,
                                                Color.magenta, 0.2f));
        maskGroup.add(Mask.BandMathsType.create(SynergyConstants.flagErrHighName,
                                                SynergyConstants.flagErrHighDesc,
                                                rasterWidth, rasterHeight,
                                                SynergyConstants.aerosolFlagCodingName + "." + SynergyConstants.flagErrHighName,
                                                Color.magenta, 0.2f));
        maskGroup.add(Mask.BandMathsType.create(SynergyConstants.flagCoastName,
                                                SynergyConstants.flagCoastDesc,
                                                rasterWidth, rasterHeight,
                                                SynergyConstants.aerosolFlagCodingName + "." + SynergyConstants.flagCoastName,
                                                Color.magenta, 0.2f));

        Band targetBand = new Band(SynergyConstants.aerosolFlagCodingName, ProductData.TYPE_UINT16, rasterWidth,
                                   rasterHeight);
        targetBand.setDescription(SynergyConstants.aerosolFlagCodingDesc);
        targetBand.setSampleCoding(aerosolFlagCoding);
        targetProduct.addBand(targetBand);
    }

    /**
     * Class representing a set of Angstroem parameters
     * (as specified in IDL breadboard)
     */
    public static class AngstroemParameters {

        private int[] indexPairs;
        private double[] weightPairs;
        private float value;

        public AngstroemParameters() {
            indexPairs = new int[2];
            weightPairs = new double[2];
        }

        public int[] getIndexPairs() {
            return indexPairs;
        }

        public double[] getWeightPairs() {
            return weightPairs;
        }

        public double getValue() {
            return value;
        }

        public void setIndexPairs(int index, int value) {
            indexPairs[index] = value;
        }

        public void setWeightPairs(int index, double value) {
            weightPairs[index] = value;
        }

        public void setValue(float value) {
            this.value = value;
        }
    }
}
