package Java.org.network.mana.base_components;

import Java.org.network.mana.functions.MHPFunctions;
import Java.org.network.mana.mana.MANA_Globals;
import Java.org.network.mana.utils.BoolArray;
import Java.org.network.mana.utils.BufferedFloatArray;
import Java.org.network.mana.utils.DataWrapper;
import Java.org.network.mana.utils.Utils;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class MANANeurons extends LIFNeurons {

	public static final double ln2 = Math.log(2);

	// TODO: Make separately settable, i.e. not static and not final
	// as well as put some code somewhere to read in values from file.

	public static final double init_tau_HP = 1E-4;
	public static final double final_tau_HP = 1E-5;
	public static final double init_tau_MHP = 0.05;
	public static final double final_tau_MHP = 1E-6;
	public static final double hp_decay = 5E-6;
	public static final double mhp_decay = 5E-6;
	public static final double default_alpha = 2;
	public static final double default_lowFR = 2;
	public static final double default_beta = 10;
	public static final double default_noiseVar = 0.7;
	public static final double mhpPressure = 2;

	public static final double default_sat_a = 300;
	public static final double default_sat_b = 0.1;
	public static final double default_sat_c = -100;
	public static final double default_exc_SF_tau = 0.2;
    public static final double default_inh_SF_tau = 0.2;


    public boolean mhpOn = true;
	
	public double lambda = init_tau_HP;
	public double eta = init_tau_MHP;
	public double noiseVar = default_noiseVar;

	
	// Can be the same or different for all neurons
	public DataWrapper alpha;
	public DataWrapper beta;
	public DataWrapper lowFRBound;
	public double ref_p;
	public DataWrapper v_reset;
	
	// MANA related properties
	// TODO: Move these and related Java.org.network.Java.org.network.mana.mana.functions to a separate class... generic IF neuron and MANA stuff separate
	public BufferedFloatArray estFR;
	public double [] ef;
	public double [] dummy; // here to test if my math is right....
	public double [] prefFR;
	public double [] threshRA;
	
	// Afferent Synapse properties
	public final BoolArray excSNon;
	public final BoolArray inhSNon;
	//public double [] normVals;
	public double [] normValsExc;
	public double [] normValsInh;
	public double [] exc_sf;
	public double [] inh_sf;
	public double [] sat_c;
	public double sat_a = default_sat_a;
	public double sat_b = default_sat_b;
	public long[] fVals;
    public boolean allExcSNon = false;
    public boolean allInhSNon = false;
    public boolean noMHP = false;


	public static MANANeurons buildFromLimits(int _N, boolean _exc, double[] xlims, double[] ylims, double[] zlims) {
	        double[] xCoors = Utils.getRandomArray(Utils.ProbDistType.UNIFORM, xlims[0], xlims[1], _N);
	        double[] yCoors = Utils.getRandomArray(Utils.ProbDistType.UNIFORM, ylims[0], ylims[1], _N);
	        double[] zCoors = Utils.getRandomArray(Utils.ProbDistType.UNIFORM, zlims[0], zlims[1], _N);
	        return new MANANeurons(_N, _exc, xCoors, yCoors, zCoors);
	}

	/**
	 * Creates MANA neurons of the specified polarity with default parameters.
	 * @param _N size of group
	 * @param _exc polarity (excitatory: true, inhibitory: false)
	 */
	public MANANeurons(int _N, boolean _exc) {
		super(_N, _exc);
		dummy = new double[N];
		threshRA = new double[N];
		estFR = new BufferedFloatArray(N);
		prefFR = new double[N];
		ef = new double[N];
		exc_sf = new double[N];
		Arrays.fill(exc_sf, 1);
		inh_sf = new double[N];
		Arrays.fill(inh_sf, 1);
		normValsExc = new double[N];
		normValsInh = new double[N];
		sat_c = new double[N];
		inhSNon = new BoolArray(N);
		excSNon = new BoolArray(N);
		fVals = new long[N];
		alpha = new DataWrapper(N, true, default_alpha);
		beta = new DataWrapper(N, true, default_beta);
		lowFRBound = new DataWrapper(N, true, default_lowFR);


		Arrays.fill(prefFR, 1.0);
		Arrays.fill(ef, 0.001);
		Arrays.fill(threshRA, init_thresh);
		Arrays.fill(exc_sf, 1);
		Arrays.fill(inh_sf, 1);
		Arrays.fill(dummy, 0.001);
		Arrays.fill(sat_c, default_sat_c);
		for(int ii=0; ii < N; ++ii) {
			estFR.setData(ii, 1.0f);
			estFR.setBuffer(ii, 1.0f);
			normValsExc[ii] = newNormVal(ii);
		}

		System.arraycopy(normValsExc, 0, normValsInh, 0, N);
	}

	/**
	 * Creates MANA neurons of the specified polarity with default parameters.
	 * @param _N size of group
	 * @param _exc polarity (excitatory: true, inhibitory: false)
	 */
	public MANANeurons(int _N, boolean _exc, double[] xCoor, double[] yCoor, double[] zCoor) {
		this(_N, _exc);
		if(xCoor != null &&
				yCoor != null &&
				zCoor != null) {
			for (int ii = 0; ii < _N; ++ii) {
				xyzCoors[ii][0] = xCoor[ii];
				xyzCoors[ii][1] = yCoor[ii];
				xyzCoors[ii][2] = zCoor[ii];
			}
		}
	}


	/**
	 * Updates everything placing the results in buffers. DOES NOT modify local variables.
	 * -The voltage & puts who spiked into a buffer as well as the new most recent spike times
	 * -The estimated firing rate
	 * -The threshold
	 * -Calculates the excitatory and inhibitory scale factors
     * -Updates preferred firing rates
     * -Calculates new f+ and f- values for new preferred firing rates
	 * @param spkBuffer
	 * @param time
	 * @param dt
	 */
	public void performFullUpdate(BoolArray spkBuffer, double[] pfrDts, final double time, final double dt) {
	    update(dt, time, spkBuffer);
	    updateEstFR(dt);
	    updateThreshold(dt);
        descaleNormVals();
	    calcScaleFacs();
		if (mhpOn && !(allExcSNon && allInhSNon) && time > 20000) {

			for(int ii=0; ii<N; ++ii) {
				if(prefFR[ii] < MANA_Globals.MIN_PFR) {
					prefFR[ii] = MANA_Globals.MIN_PFR + (1+ThreadLocalRandom.current().nextGaussian() * 0.1);
				}
				if(prefFR[ii] > MANA_Globals.MAX_PFR) {
					prefFR[ii] = MANA_Globals.MAX_PFR;
				}

				//double lwVal = Math.exp((MANA_Globals.MIN_PFR - estFR.getData(ii))/prefFR[ii]);
				//double hgVal = -Math.exp((estFR.getData(ii)-MANA_Globals.MAX_PFR)/prefFR[ii]);

				//lwVal *= mhpPressure * MHPFunctions.mhpLTPTerm(prefFR[ii], default_beta, default_lowFR) / (inDegree[ii]+1);
				//hgVal *= mhpPressure * MHPFunctions.mhpLTDTerm(prefFR[ii], default_alpha, default_lowFR) / (inDegree[ii]+1);

//                if(excSNon.get(ii) && inhSNon.get(ii)) {
//                    prefFR[ii] += (dt*final_tau_MHP/(double)(inDegree[ii]+1)) * pfrDts[ii] * ((1+ThreadLocalRandom.current().nextGaussian()) * noiseVar); //* (pfrDts[ii] + lwVal + hgVal);
//                } else {
                    prefFR[ii] += (dt*eta/(double)(inDegree[ii]+1)) * pfrDts[ii] * ((1+ThreadLocalRandom.current().nextGaussian()) * noiseVar); //* (pfrDts[ii] + lwVal + hgVal);
//                }


			}
//			if(isExcitatory()) {
				MHPFunctions.calcfTerm(prefFR, fVals, default_alpha, default_beta, default_lowFR);
//			} else {
//				MHPFunctions.calcfTerm(prefFR, fVals, default_alpha, default_beta, 2);
//			}
		}
		if((time <= 20000 && mhpOn) || noMHP) {
			for(int ii=0; ii<N; ++ii) {
				prefFR[ii] = estFR.getData(ii);
			}
		}
//		if(mhpFrozen) {
//			for(int ii=0; ii<N; ++ii) {
//				//prefFR[ii] = estFR.getData(ii);
//				prefFR[ii] = 20;
//				//estFR.setBuffer(ii, 10);
//			}
//		}

		calcNewNorms();
		scaleNormVals();
        lambda += dt * (final_tau_HP-lambda) * hp_decay;
        eta += dt  * (final_tau_MHP-eta) * mhp_decay;
    }

    /**
     * For triggered normVals, de scales them so that the new scaling can be applied.
     */
    private void descaleNormVals() {
	    if(!allExcSNon) {
            for (int ii = 0; ii < N; ++ii) {
                if (excSNon.get(ii)) {
                    normValsExc[ii] /= exc_sf[ii];
                }
            }
        } else  {
            for (int ii = 0; ii < N; ++ii) {
                normValsExc[ii] /= exc_sf[ii];
            }
        }
        if(!allInhSNon) {
            for (int ii = 0; ii < N; ++ii) {
                if (inhSNon.get(ii)) {
                    normValsInh[ii] /= inh_sf[ii];
                }
            }
        } else {
            for (int ii = 0; ii < N; ++ii) {
                normValsInh[ii] /= inh_sf[ii];
            }
        }

    }

    private void scaleNormVals() {
        for(int ii=0; ii<N; ++ii){
            normValsInh[ii] *= inh_sf[ii];
        }
        for(int ii=0; ii<N; ++ii){
            normValsExc[ii] *= exc_sf[ii];
        }

    }

    /**
     * Checks to see if synaptic sums have exceeded their scaled norm vals. If they all have
     * sets allExcSNon and/or allInhSNon to true.
     * @param excSums
     * @param inhSums
     */
    public void updateTriggers(double[] excSums, double[] inhSums) {
        if(!allExcSNon) {
            boolean allOn = true;
            for(int ii=0; ii<N; ++ii) {
				if((excSums[ii] >= normValsExc[ii]) && !excSNon.get(ii)) {
					normValsExc[ii] = excSums[ii];
					excSNon.set(ii, true);
					System.out.println();
					System.out.println(id + " " + ii + " EXCIT TRIPPED");
				}

				allOn &= excSNon.get(ii);
            }
            allExcSNon = allOn;
        }
        if(!allInhSNon) {
            boolean allOn = true;
            for(int ii=0; ii<N; ++ii) {
            	if((inhSums[ii] >= normValsInh[ii]) && !inhSNon.get(ii)) {
            		normValsInh[ii] = inhSums[ii];
					inhSNon.set(ii, true);
					System.out.println();
					System.out.println(id + " " + ii + " INHIB TRIPPED");
				}

                allOn &= inhSNon.get(ii);
            }
            allInhSNon = allOn;
        }
    }

    /**
     * Calculates new normalization values based on pref. firing rate for all non-triggered neurons for
     * both types...
     */
    private void calcNewNorms() {

    	if(!allExcSNon) {
    		for(int ii=0; ii<N; ++ii) {
    			if(!excSNon.get(ii)) {
    				normValsExc[ii] = newNormVal(ii);
				}
			}
		}

    	if(!allInhSNon) {
    		for(int ii=0; ii<N; ++ii) {
    			if(!inhSNon.get(ii)) {
    				normValsInh[ii] = newNormVal(ii)/5;
				}
			}
		}

    }

	@Override
	public void spikeAction(int neuNo, double time) {
		super.spikeAction(neuNo, time);
		ef[neuNo]++;
	}
	
	/**
	 * Updates the estimated firing rate of the neurons placing the new
	 * values in a buffer
	 * @param dt
	 */
	public void updateEstFR(double dt) {
		for(int ii=0; ii<N; ++ii) {
			double tauA = 10000 / Math.sqrt(prefFR[ii]);
			ef[ii] -= dt * ef[ii]/tauA;
			if (Double.isNaN(ef[ii])) {
				System.out.println("nan");
			}
			dummy[ii] += dt * (ef[ii]/tauA - dummy[ii]);
			estFR.setBuffer(ii,(float) (dummy[ii] * 1000));//estFR[ii] + (dt *(1000*ef[ii] - estFR[ii]));
		}
	}
	
	/**
	 * Updates the threshold based on the homeostatic plasticity rules using
	 * preferred and estimated firing rate. Updates threshold directly, not to a buffer
	 * @param dt
	 */
	public void updateThreshold(double dt) {
		for(int ii=0; ii<N; ++ii) {
//			double estISI = 1/(estFR.getData(ii)+0.001) - ref_p/1000.0;
//			double estTerm = Math.exp(estISI/tau_m.get(ii));
//			double e_l_hat = v_reset.get(ii) - thresh[ii]*estTerm;
//			e_l_hat /= 1-estTerm;
//			double prefISI = 1/prefFR[ii] - ref_p/1000.0;
//			double prefThresh = e_l_hat - (e_l_hat-v_reset.get(ii))/Math.exp(prefISI/tau_m.get(ii));
//
//			double thDelta = dt * lambda * (prefThresh - thresh[ii]);
//
//			if(Math.abs(thresh[ii]-init_thresh) < Math.abs(thresh[ii]+thDelta-init_thresh)) {
//				double softBound = Math.exp(-Math.abs(init_thresh - thresh[ii])/4.0);
//				thresh[ii] += thDelta * softBound;
//			} else {
//				thresh[ii] += thDelta;
//			}
			thresh.setAt(thresh.get(ii) + dt * lambda * Math.log((estFR.getData(ii)+0.0001)/(prefFR[ii]+0.0001)), ii);
			if (Double.isNaN(thresh.get(ii))) {
				System.out.println("NaN th");
				break;
			}
			threshRA[ii] = thresh.get(ii) * (lambda*dt) + threshRA[ii]*(1-(lambda*dt));
		}
	}

	public void calcScaleFacs() {
//		for(int ii=0; ii<N; ++ii){
//			double logDiff = Math.log(estFR.getData(ii)) - Math.log(prefFR[ii]);
//			exc_sf[ii] -= MANA_Globals.dt * logDiff * 5E-6;
//			inh_sf[ii] += MANA_Globals.dt * logDiff * 5E-6;
//
//		}
//
//		for(int ii=0; ii<N; ++ii){
//			if(exc_sf[ii] > 10) {
//				exc_sf[ii] = 10;
//			}
//			if(exc_sf[ii] < 0.1) {
//				exc_sf[ii] = 0.1;
//			}
//		}
//
//		for(int ii=0; ii<N; ++ii){
//			if(inh_sf[ii] > 10) {
//				inh_sf[ii] = 10;
//			}
//			if(inh_sf[ii] < 0.1) {
//				inh_sf[ii] = 0.1;
//			}
//		}

//
//        for(int ii=0; ii<N; ++ii) {
//            double rat = exc_sf[ii]/inh_sf[ii];
//            rat += MANA_Globals.dt*lambda* Math.log(prefFR[ii]/estFR.getData(ii));
//            rat /= rat+1;
//            if(rat > 0.9) {
//                rat = 0.9;
//            }
//            if(rat < 0.1){
//                rat = 0.1;
//            }
//            exc_sf[ii] = 2*(rat);
//            inh_sf[ii] = 2*(1-rat);
//        }

     //   Utils.retainBounds(exc_sf, 10, 0.1);
     //   Utils.retainBounds(inh_sf, 10, 0.1);

	    for(int ii=0; ii<N; ++ii) {
	        exc_sf[ii] = 0.5+1.5/(1+Math.exp(-(prefFR[ii])/default_exc_SF_tau) + ln2);
        }
        for(int ii=0; ii<N; ++ii) {
            inh_sf[ii] = 0.5+1.5/(1+Math.exp(-(thresh.get(ii) - threshRA[ii])/default_inh_SF_tau) + ln2);
        }
    }


    /**
     * For all total excitatory sums that haven't been set by normalization, checks if they
     * have exceeded or are equal to what the normalization value should be. Returns whether or
     * not all neurons have been set.
     * @param sums
     * @return
     */
    public boolean checkExcSums(double[] sums) {
		boolean allUp = true;
		for(int ii=0; ii<N; ++ii) {
		    if(!excSNon.get(ii)) {
		        boolean decision = sums[ii] > normValsExc[ii];
		        excSNon.set(ii, decision);
		        allUp &= decision;
            }
		}
		return allUp;
	}

    /**
     * For all total inhibitory sums that haven't been set by normalization, checks if they
     * have exceeded or are equal to what the normalization value should be. Returns whether or
     * not all neurons have been set.
     * @param sums
     * @return
     */
    public boolean checkInhSums(double[] sums) {
        boolean allUp = true;
        for(int ii=0; ii<N; ++ii) {
            if(!inhSNon.get(ii)) {
                boolean decision = sums[ii] > normValsInh[ii];
                inhSNon.set(ii, decision);
                allUp &= decision;
            }
        }
        return allUp;
    }

	/**
	 * Also sets the norm values appropriately
	 * @param sat_c
	 */
	public void setSatC(double [] sat_c) {
		return;
//    	this.sat_c = new double[N];
//    	System.arraycopy(sat_c, 0, this.sat_c, 0, N);
//    	for(int ii=0; ii<N; ++ii) {
//    		double nnv = newNormVal(ii);
//    		normValsExc[ii] = nnv; //* exc_sf[ii];
//    		normValsInh[ii] = nnv; //* inh_sf[ii];
//		}
	}

	/**
	 * 
	 * @param index
	 * @return the base synaptic normalization value the neuron at the specified
	 * index should have based on its preferred firing rate.
	 */
	public double newNormVal(int index) {
		return sat_a/(1+Utils.expLT0Approx(-sat_b*prefFR[index]))
				+ sat_c[index];
	}

	public int[] getProperInDegrees(SpikingNeuron src) {
		if (src.isExcitatory()) {
			return excInDegree;
		} else {
			return inhInDegree;
		}
	}

	public boolean getAllNrmOn(boolean exc) {
	    return exc ? allExcSNon : allInhSNon;
    }


    public void setPrefFRs(double pfr) {
		for(int ii=0; ii<N; ++ii) {
			prefFR[ii]=pfr;
		}
	}

}