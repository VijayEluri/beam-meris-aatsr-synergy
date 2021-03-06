/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.esa.beam.synergy.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.synergy.util.SynergyConstants;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;

/**
 * @author akheckel
 */
@OperatorMetadata(alias = "synergy.Upscale",
                  version = "1.2",
                  authors = "Andreas Heckel, Olaf Danne",
                  copyright = "(c) 2009 by A. Heckel",
                  description = "AOT upscaling of interpolated data.", internal = true)
public class UpscaleOp extends Operator {

    private static final String PRODUCT_NAME = "SYNERGY UPSCALED AOT";
    private static final String PRODUCT_TYPE = "SYNERGY UPSCALED AOT";

    private static final String AOT_NAME = SynergyConstants.OUTPUT_AOT_BAND_NAME;
    private static final String ERR_NAME = SynergyConstants.OUTPUT_AOTERR_BAND_NAME;
    private static final String MODEL_NAME = SynergyConstants.OUTPUT_AOTMODEL_BAND_NAME;

    @SourceProduct(alias = "aerosol",
                   label = "Name (Downscaled aerosol product)",
                   description = "Select a Synergy aerosol product.")
    private Product sourceProduct;

    @SourceProduct(alias = "synergy",
                   label = "Name (Original Synergy product)",
                   description = "Select a Synergy product.")
    private Product originalProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    @Parameter(defaultValue = "7", label = "BoxSize to invert in Pixel (n x n)", interval = "[1, 100]")
    private int scalingFactor;

    private int offset;

    private int sourceRasterWidth;
    private int sourceRasterHeight;
    private int targetRasterWidth;
    private int targetRasterHeight;


    @Override
    public void initialize() throws OperatorException {

        offset = scalingFactor / 2;
        sourceRasterWidth = sourceProduct.getSceneRasterWidth();
        sourceRasterHeight = sourceProduct.getSceneRasterHeight();
        targetRasterWidth = originalProduct.getSceneRasterWidth();
        targetRasterHeight = originalProduct.getSceneRasterHeight();

        createTargetProduct();
//        targetProduct.setPreferredTileSize(512, 512);
        // the upscaling seems to work properly only for a 'single' tile
        // todo: fix
        targetProduct.setPreferredTileSize(targetRasterWidth + 1, targetRasterHeight + 1);
        setTargetProduct(targetProduct);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle tarRec = targetTile.getRectangle();

        int srcX = (tarRec.x - offset) / scalingFactor;
        int srcY = (tarRec.y - offset) / scalingFactor;
        int srcWidth = tarRec.width / scalingFactor + 1;
        int srcHeight = tarRec.height / scalingFactor + 1;
        if (srcX >= sourceRasterWidth) {
            srcX = sourceRasterWidth - 2;
            srcWidth = 2;
        }
        if (srcY >= sourceRasterHeight) {
            srcY = sourceRasterHeight - 2;
            srcHeight = 2;
        }
        final Rectangle srcRec = new Rectangle(srcX, srcY, srcWidth, srcHeight);

        Band srcBand;
        Tile srcTile;
        if (originalProduct.containsBand(targetBand.getName())) {
            srcBand = originalProduct.getBand(targetBand.getName());
            if (srcBand != null) {
                srcTile = getSourceTile(srcBand, tarRec);
                targetTile.setRawSamples(srcTile.getRawSamples());
            }
        } else if (sourceProduct.containsBand(targetBand.getName())) {
            srcBand = sourceProduct.getBand(targetBand.getName());
            srcTile = getSourceTile(srcBand, srcRec);
            if (targetBand.getName().equals(AOT_NAME)
                || targetBand.getName().equals(ERR_NAME)
                || targetBand.getName().startsWith(MODEL_NAME)
                || targetBand.isFlagBand()) {

                upscaleTileCopy(srcTile, targetTile, pm);
            } else {
                upscaleTileBilinear(srcTile, targetTile);
            }
        }
    }

    private void createTargetProduct() {

        targetProduct = new Product(PRODUCT_NAME, PRODUCT_TYPE, targetRasterWidth, targetRasterHeight);

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(originalProduct, targetProduct);
        targetProduct.removeTiePointGrid(targetProduct.getTiePointGrid("latitude"));
        targetProduct.removeTiePointGrid(targetProduct.getTiePointGrid("longitude"));
        ProductUtils.copyTiePointGrids(originalProduct, targetProduct);
        ProductUtils.copyFlagBands(originalProduct, targetProduct);

        for (String fcName : sourceProduct.getFlagCodingGroup().getNodeNames()) {
            if (!targetProduct.getFlagCodingGroup().contains(fcName)) {
                FlagCoding srcFlagCoding = sourceProduct.getFlagCodingGroup().get(fcName);
                ProductUtils.copyFlagCoding(srcFlagCoding, targetProduct);
            }
        }
        ProductNodeGroup<Mask> targetMaskGroup = targetProduct.getMaskGroup();
        ProductNodeGroup<Mask> sourceMaskGroup = sourceProduct.getMaskGroup();
        for (String bmName : sourceMaskGroup.getNodeNames()) {
            if (!targetMaskGroup.contains(bmName)) {
                Mask srcMask = sourceMaskGroup.get(bmName);
                srcMask.getImageType().transferMask(srcMask, targetProduct);
            }
        }

        for (Band srcBand : sourceProduct.getBands()) {
            String bandName = srcBand.getName();
            if (originalProduct.containsBand(bandName)) {
                if (!originalProduct.getBand(bandName).isFlagBand()) {
                    ProductUtils.copyBand(bandName, originalProduct, targetProduct);
                }
            } else {
                Band targetBand = new Band(bandName, srcBand.getDataType(), targetRasterWidth, targetRasterHeight);
                targetBand.setDescription(srcBand.getDescription());
                targetBand.setNoDataValue(srcBand.getNoDataValue());
                targetBand.setNoDataValueUsed(true);
                FlagCoding srcFlagCoding = srcBand.getFlagCoding();
                if (srcFlagCoding != null) {
                    FlagCoding tarFlagCoding = targetProduct.getFlagCodingGroup().get(srcFlagCoding.getName());
                    targetBand.setSampleCoding(tarFlagCoding);
                }
                targetProduct.addBand(targetBand);
            }
        }
    }

    private void upscaleTileBilinear(Tile srcTile, Tile tarTile) {

        final Rectangle tarRec = tarTile.getRectangle();
        final int tarX = tarRec.x;
        final int tarY = tarRec.y;
        final int tarWidth = tarRec.width;
        final int tarHeight = tarRec.height;

        for (int iTarY = tarY; iTarY < tarY + tarHeight; iTarY++) {
            int iSrcY = (iTarY - offset) / scalingFactor;
            if (iSrcY >= srcTile.getHeight() - 1) {
                iSrcY = srcTile.getHeight() - 2;
            }
            float yFac = (float) (iTarY - offset) / scalingFactor - iSrcY;
            for (int iTarX = tarX; iTarX < tarX + tarWidth; iTarX++) {
                int iSrcX = (iTarX - offset) / scalingFactor;
                if (iSrcX >= srcTile.getWidth() - 1) {
                    iSrcX = srcTile.getWidth() - 2;
                }
                float xFrac = (float) (iTarX - offset) / scalingFactor - iSrcX;
                float erg = (1.0f - xFrac) * (1.0f - yFac) * srcTile.getSampleFloat(iSrcX, iSrcY);
                erg += (xFrac) * (1.0f - yFac) * srcTile.getSampleFloat(iSrcX + 1, iSrcY);
                erg += (1.0f - xFrac) * (yFac) * srcTile.getSampleFloat(iSrcX, iSrcY + 1);
                erg += (xFrac) * (yFac) * srcTile.getSampleFloat(iSrcX + 1, iSrcY + 1);
                tarTile.setSample(iTarX, iTarY, erg);
            }
        }
    }

    private void upscaleTileCopy(Tile srcTile, Tile tarTile, ProgressMonitor pm) {

        final Rectangle tarRec = tarTile.getRectangle();
        final int tarX = tarRec.x;
        final int tarY = tarRec.y;
        final int tarWidth = tarRec.width;
        final int tarHeight = tarRec.height;

        for (int iTarY = tarY; iTarY < tarY + tarHeight; iTarY++) {
            int iSrcY = iTarY / scalingFactor;
            if (iSrcY >= srcTile.getHeight() - 1) {
                iSrcY = srcTile.getHeight() - 2;
            }
            for (int iTarX = tarX; iTarX < tarX + tarWidth; iTarX++) {
                if (pm.isCanceled()) {
                    break;
                }
                int iSrcX = iTarX / scalingFactor;
                if (iSrcX >= srcTile.getWidth() - 1) {
                    iSrcX = srcTile.getWidth() - 2;
                }
                float erg = srcTile.getSampleFloat(iSrcX, iSrcY);
                tarTile.setSample(iTarX, iTarY, erg);
            }
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(UpscaleOp.class);
        }
    }
}
