package base_components;

public class SynMatDataAddOn {

    public final SynapseMatrix coordMat;

    public double [] values;

    private final int nilFac;


    public SynMatDataAddOn(final SynapseMatrix coordMat, final int nilFac) {
        this.coordMat = coordMat;
        this.nilFac = nilFac;
        values = new double[coordMat.getNnz()/coordMat.nILFac * nilFac];
    }

    /**
     * Given some new ordering reorder values (especially for altering the number
     * of datums). -1 in newCoos indicates a new datum in that location in
     * the new values. The length of newCoos is going to be the new length of
     * values divided by the interleaving factor and coordinates in newCoos are
     * in the range [-1 values.length] indicating that the value in that location
     * in values should be in the same place it exists in newCoos in the new values.
     * @param newCoos
     */
    public void rearrange(int[] newCoos) {
        double [] newVals = new double[newCoos.length * nilFac];
        for(int ii=0, n=newCoos.length; ii<n; ++ii) {
            if (newVals[ii] == -1) continue;
            for(int jj=0; jj<nilFac; ++jj) {
                newVals[ii*nilFac + jj] = values[newCoos[ii]*nilFac + jj];
            }
        }
    }

}