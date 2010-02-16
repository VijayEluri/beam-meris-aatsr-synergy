/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.esa.beam.synergy.operators;

import java.util.ArrayList;
import java.io.File;

import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.dataio.envisat.EnvisatConstants;

/**
 * Constants used in Synergy aerosol retrieval
 *
 * @author Andreas Heckel, Olaf Danne
 * @version $Revision: 8111 $ $Date: 2010-01-27 18:54:34 +0100 (Mi, 27 Jan 2010) $
 */
public class RetrieveAerosolConstants {

    // Constants
    public static final String PROCESSOR_NAME = "Synergy AOT Processor";
    public static final String PROCESSOR_VERSION = "0.1";
    public static final String PROCESSOR_COPYRIGHT = "Copyright (C) 2009 by A.Heckel";

    public static final String REQUEST_TYPE = "SYNAOT";
    
    public static final String LOGGER_NAME = "beam.processor.synaer";
    public static final String LOG_MSG_HEADER = "Logfile generated by " + PROCESSOR_NAME + ", version " + PROCESSOR_VERSION;
    public static final String LOG_MSG_LOAD_GEOMETRY_START = "Loading geometry tie point grids...";
    public static final String LOG_MSG_LOAD_GEOMETRY_END = "Loaded geometry tie point grids";
    public static final String LOG_MSG_PROC_START = "Converting radiances to reflectances...";
    public static final String LOG_MSG_PROC_BAND_1 = "Processing band '";
    public static final String LOG_MSG_PROC_BAND_2 = "'...";
    public static final String LOG_MSG_UNSUPPORTED_PRODUCT = "The requested input product is not supported. Currently, only Collocated MERIS-AATSR products are supported.";

    public static final String DEFAULT_LOG_PREFIX = "synaot";
    public static final String DEFAULT_INPUT_PRODUCT_TYPE = "COLLOCATED";
    public static final String DEFAULT_OUTPUT_FORMAT = DimapProductConstants.DIMAP_FORMAT_NAME;
    public static final String DEFAULT_OUTPUT_PRODUCT_FILE_NAME = "SYN_AOT2P.dim";

    public static final String SOIL_SPEC_PARAM_NAME    = "soilspec";
    public static final String SOIL_SPEC_PARAM_DEFAULT = "spec_soil.dat";
    public static final String SOIL_SPEC_PARAM_LABEL   = "Soil surface reflectance spectrum";
    public static final String SOIL_SPEC_PARAM_DESCRIPTION = "File containing soil surface reflectance spectrum";

    public static final String VEG_SPEC_PARAM_NAME    = "vegspec";
    public static final String VEG_SPEC_PARAM_DEFAULT = "spec_veg.dat";
    public static final String VEG_SPEC_PARAM_LABEL   = "Vegetation surface reflectance spectrum";
    public static final String VEG_SPEC_PARAM_DESCRIPTION = "File containing vegetation surface reflectance spectrum";
    
    public static final String LUT_PATH_PARAM_NAME    = "lutpath";
    public static final String LUT_OCEAN_PATH_PARAM_LABEL = "Path to LUTs for ocean aerosol algorithm";
    public static final String LUT_LAND_PATH_PARAM_LABEL = "Path to LUTs for land aerosol algorithm";
    public static final String LUT_PATH_PARAM_DESCRIPTION = "File path to LookUpTables root directory";
    public static final String LUT_LAND_PATH_PARAM_DEFAULT = "C:/synergy/aerosolLandLUTs/bin";
    public static final String LUT_OCEAN_PATH_PARAM_DEFAULT = "C:/synergy/aerosolOceanLUTs";
    public static final String[] LUT_LAND_AATSR_WAVELEN = {"00550.00","00665.00","00865.00","01610.00"};
    public static final String[] LUT_LAND_MERIS_WAVELEN = {"00412.00","00442.00","00490.00","00510.00","00560.00",
                                                      "00620.00","00665.00","00681.00","00708.00","00753.00",
                                                      "00778.00","00865.00","00885.00"};
    
    public static final String AEROSOL_MODEL_PARAM_NAME    = "aerosolmodel";
    public static final String AEROSOL_MODEL_PARAM_DEFAULT = "8,2";
    public static final String AEROSOL_MODEL_PARAM_LABEL   = "List of land aerosol models";
    public static final String AEROSOL_MODEL_PARAM_DESCRIPTION = "Comma sep. list of aerosol model identifiers";

    public static final String OUTPUT_PRODUCT_NAME_NAME = "targetname";
    public static final String OUTPUT_PRODUCT_NAME_DEFAULT = "SYNERGY LAND AEROSOL";
    public static final String OUTPUT_PRODUCT_NAME_DESCRIPTION = "Product name of the target data set";
    public static final String OUTPUT_PRODUCT_NAME_LABEL = "Product name";

    public static final String OUTPUT_PRODUCT_TYPE_NAME = "targettype";
    public static final String OUTPUT_PRODUCT_TYPE_DEFAULT = "AEROSOL";
    public static final String OUTPUT_PRODUCT_TYPE_DESCRITPION = "Product type of the target data set";
    public static final String OUTPUT_PRODUCT_TYPE_LABEL = "Product type";

    // aot output
    public static final String OUTPUT_AOT_BAND_NAME = "aot";
    public static final String OUTPUT_AOT_BAND_DESCRIPTION = "MERIS AATSR Synergy AOT";
    public static final double OUTPUT_AOT_BAND_NODATAVALUE = -1.0;
    public static final boolean OUTPUT_AOT_BAND_NODATAVALUE_USED = Boolean.TRUE;

    // ang output
    public static final String OUTPUT_ANG_BAND_NAME = "ang";
    public static final String OUTPUT_ANG_BAND_DESCRIPTION = "MERIS AATSR Synergy Angstrom Parameter";
    public static final double OUTPUT_ANG_BAND_NODATAVALUE = -1.0;
    public static final boolean OUTPUT_ANG_BAND_NODATAVALUE_USED = Boolean.TRUE;

    // aot uncertainty output
    public static final String OUTPUT_AOTERR_BAND_NAME = "aot_uncertainty";
    public static final String OUTPUT_AOTERR_BAND_DESCRIPTION = "MERIS AATSR Synergy uncertainty of AOT";
    public static final double OUTPUT_AOTERR_BAND_NODATAVALUE = -1.0;
    public static final boolean OUTPUT_AOTERR_BAND_NODATAVALUE_USED = Boolean.TRUE;

    // ang uncertainty output
    public static final String OUTPUT_ANGERR_BAND_NAME = "ang_uncertainty";
    public static final String OUTPUT_ANGERR_BAND_DESCRIPTION = "MERIS AATSR Synergy uncertainty of Angstrom Parameter";
    public static final double OUTPUT_ANGERR_BAND_NODATAVALUE = -1.0;
    public static final boolean OUTPUT_ANGERR_BAND_NODATAVALUE_USED = Boolean.TRUE;

    // land aerosol model output
    public static final String OUTPUT_AOTMODEL_BAND_NAME = "land_aerosol_model";
    public static final String OUTPUT_AOTMODEL_BAND_DESCRIPTION = "MERIS AATSR Synergy LAnd Aerosol Model";
    public static final int OUTPUT_AOTMODEL_BAND_NODATAVALUE = -1;
    public static final boolean OUTPUT_AOTMODEL_BAND_NODATAVALUE_USED = Boolean.TRUE;

    // glint output
    public static final String OUTPUT_GLINT_BAND_NAME = "glint";
    public static final String OUTPUT_GLINT_BAND_DESCRIPTION = "Glint retrieval for first band used (debug output)";
    public static final double OUTPUT_GLINT_BAND_NODATAVALUE = -1.0;
    public static final boolean OUTPUT_GLINT_BAND_NODATAVALUE_USED = Boolean.TRUE;

    // windspeed output
    public static final String OUTPUT_WS_BAND_NAME = "windspeed";
    public static final String OUTPUT_WS_BAND_DESCRIPTION = "Windspeed retrieval from Glint algorithm (debug output)";
    public static final double OUTPUT_WS_BAND_NODATAVALUE = -1.0;
    public static final boolean OUTPUT_WS_BAND_NODATAVALUE_USED = Boolean.TRUE;

    public static final String INPUT_BANDS_PREFIX_MERIS = "reflectance_";
    public static final String INPUT_BANDS_PREFIX_AATSR_NAD = "reflec_nadir";
    public static final String INPUT_BANDS_PREFIX_AATSR_FWD = "reflec_fward";

    public static final String INPUT_BANDS_SUFFIX_AATSR = "AATSR";
    public static final String INPUT_BANDS_SUFFIX_MERIS = "MERIS";
    // todo: for FUB demo products only. change back later!
//    public static final String INPUT_BANDS_SUFFIX_AATSR = "S";
//    public static final String INPUT_BANDS_SUFFIX_MERIS = "M";

    public static final int[] EXCLUDE_INPUT_BANDS_MERIS = {11,15};
    public static final int[] EXCLUDE_INPUT_BANDS_AATSR = null;
    static String OUTPUT_ERR_BAND_NAME;

    public static final float[] refractiveIndex = new float[]{1.3295f,1.329f,1.31f,1.3295f,1.31f,1.3295f};
    public static final float[] rhoFoam = new float[]{0.2f, 0.2f, 0.1f, 0.2f, 0.1f, 0.2f};

    // this is preliminary!
    public static float MERIS_12_SOLAR_FLUX = 930.0f;
    public static float MERIS_13_SOLAR_FLUX = 902.0f;
    public static float MERIS_14_SOLAR_FLUX = 870.0f;

    public static final String CONFID_NADIR_FLAGS_AATSR = "confid_flags_nadir_AATSR";
    public static final String CONFID_FWARD_FLAGS_AATSR = "confid_flags_fward_AATSR";
    public static final String CLOUD_NADIR_FLAGS_AATSR = "cloud_flags_nadir_AATSR";
    public static final String CLOUD_FWARD_FLAGS_AATSR = "cloud_flags_fward_AATSR";

    public static final String L1_FLAGS_MERIS = "l1_flags_MERIS";
    public static final String CLOUD_FLAG_MERIS = "cloud_flag_MERIS";

}
