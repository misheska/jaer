/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.proximity;

import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.EventRateEstimator;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasLEDControl;

/**
 * Detects proximity of hand or object by looking for events generated by response of sensor to flashing LED which illuminates the nearby scene.
 * Fires PROXMITY PropertyChange events on change of detected proximity.
 * 
 * @author tobi
 * @see #PROXIMITY
 */
@Description("Proximity detection using flashing LED that illuminates nearby scene")
public class ProximityLEDDetector extends EventFilter2D implements Observer, FrameAnnotater {

    private HasLEDControl ledControl = null;
    private boolean proximityDetected = false;
    /** Event that is fired on change of proximity */
    public static final String PROXIMITY = "proximity";
    private Timer ledTimer = null;
    private EventRateEstimator rateEstimator = null;
    private int lastLEDChangeTimestampUs = 0;
    private boolean lastLEDOn = true;
    private long lastCommandSentNs = 0;
    private Histogram histOn = new Histogram(), histOff = new Histogram();
    TextRenderer renderer;
    // parameters
    private int histNumBins = getInt("histNumBins", 10);
    private int histBinSizeUs = getInt("histBinSizeUs", 1000);
    private float histCountScale = getFloat("histCountScale", 0.1f);
    private long periodMs = getInt("periodMs", 20);
    protected int countThreshold = getInt("countThreshold", 100);
    protected float maxCOV = getFloat("maxCOV", 1);

    public ProximityLEDDetector(AEChip chip) {
        super(chip);
        rateEstimator = new EventRateEstimator(chip);
        setEnclosedFilterChain(new FilterChain(chip));
        getEnclosedFilterChain().add(rateEstimator);
        setPropertyTooltip("histCountScale", "count scale for histogram events after sync");
        chip.addObserver(this);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        rateEstimator.filterPacket(in);
        for (BasicEvent o : in) {
            PolarityEvent e = (PolarityEvent) o;
            if (e.isSpecial()) { // got sync event indicating that camera has changed LED
                if (ledControl.getLEDState(0) == HasLEDControl.LEDState.ON) {
                    lastLEDOn = true;
                    histOn.reset();
                } else {
                    lastLEDOn = false; // TODO sloppy
                    histOff.reset();
                }
//                int dt = e.timestamp - lastLEDChangeTimestampUs;
//                log.info(dt + " us since last LED change");
                lastLEDChangeTimestampUs = e.timestamp;
            } else {
                int dt = e.timestamp - lastLEDChangeTimestampUs;
                if (lastLEDOn) {
                    histOn.put(dt);
                } else {
                    histOff.put(dt);
                }
            }
        }
        histOn.computeStats();
        histOff.computeStats();
        if (histOn.proximityDetected() && histOff.proximityDetected()) {
            setProximityDetected(true);
        } else {
            setProximityDetected(false);
        }
        return in;

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
        if (renderer == null) {
            renderer = new TextRenderer(new Font("Monospaced", Font.PLAIN, 24), true, true);
        }
        histOn.draw(gl, 80);
        histOn.draw(gl, 40);
    }

    /**
     * @return the histNumBins
     */
    public int getHistNumBins() {
        return histNumBins;
    }

    /**
     * @param histNumBins the histNumBins to set
     */
    synchronized public void setHistNumBins(int histNumBins) {
        this.histNumBins = histNumBins;
        histOn.init();
        histOff.init();
        putInt("histNumBins", histNumBins);
    }

    /**
     * @return the histBinSizeUs
     */
    public int getHistBinSizeUs() {
        return histBinSizeUs;
    }

    /**
     * @param histBinSizeUs the histBinSizeUs to set
     */
    public void setHistBinSizeUs(int histBinSizeUs) {
        this.histBinSizeUs = histBinSizeUs;
        putInt("histBinSizeUs", histBinSizeUs);
    }

    /**
     * @return the histCountScale
     */
    public float getHistCountScale() {
        return histCountScale;
    }

    /**
     * @param histCountScale the histCountScale to set
     */
    public void setHistCountScale(float histCountScale) {
        this.histCountScale = histCountScale;
        putFloat("histCountScale", histCountScale);
    }

    /**
     * @return the periodMs
     */
    public int getPeriodMs() {
        return (int) periodMs;
    }

    /**
     * @param periodMs the periodMs to set
     */
    public void setPeriodMs(int periodMs) {
        this.periodMs = periodMs;
        putInt("periodMs", periodMs);
        if (isFilterEnabled()) {
            setFilterEnabled(false);
            setFilterEnabled(true);
        }
    }

    /**
     * @return the countThreshold
     */
    public int getCountThreshold() {
        return countThreshold;
    }

    /**
     * @param countThreshold the countThreshold to set
     */
    public void setCountThreshold(int countThreshold) {
        this.countThreshold = countThreshold;
        putInt("countThreshold", countThreshold);
    }

    /**
     * @return the maxCOV
     */
    public float getMaxCOV() {
        return maxCOV;
    }

    /**
     * @param maxCOV the maxCOV to set
     */
    public void setMaxCOV(float maxCOV) {
        this.maxCOV = maxCOV;
        putFloat("maxCOV", maxCOV);
    }

    private class Histogram {

        int[] counts = new int[getHistNumBins()];
        int overflowCount = 0;
        float meanCount = 0, meanBin = 0, stdCount = 0, stdBin = 0;
        int sumCounts = 0;

        public Histogram() {
        }

        void init() {
            counts = new int[getHistNumBins()];
            overflowCount = 0;
        }

        synchronized void reset() {
            Arrays.fill(counts, 0);
            overflowCount = 0;
        }

        synchronized void put(int dt) {
            if (dt < 0) {
                return;
            }
            int bin = dt / histBinSizeUs;
//            System.out.println("dt="+dt+" bin="+bin);
            if (bin >= histNumBins) {
                overflowCount++;
                return;
            }
            counts[bin]++;
        }

        synchronized void draw(GL gl, int y) {
            int x = 10;
            float histBinWidthPix = (float) (chip.getSizeX() - 2 * x) / histNumBins;
            if (proximityDetected) {
                gl.glColor3f(1, 0, 0);
            } else {
                gl.glColor3f(0, 0, 1);
            }
            gl.glLineWidth(4);
            gl.glBegin(GL.GL_LINE_STRIP);
            for (int i = 0; i < histNumBins; i++) {
                float yy = y + counts[i] * histCountScale;
                gl.glVertex2f(x + i * histBinWidthPix, yy);
                gl.glVertex2f(1 + x + i * histBinWidthPix, yy);
//                System.out.print(bins[i]+" ");
            }
//            System.out.println("");
            gl.glEnd();
            renderer.begin3DRendering();
            String s = String.format("count=%6.1f + %-6.1f bin=%6.1f+ %-6.1f", meanCount, stdCount, meanBin, stdBin);
            final float scale = .2f;
            renderer.draw3D(s, x, y - 5, 0, scale);
//        Rectangle2D bounds=renderer.getBounds(s);
            renderer.end3DRendering();
        }

        // call this before gettings stats
        synchronized void computeStats() {
            sumCounts = 0;
            int sumCounts2 = 0;
            int weightedCount = 0;
            for (int i = 0; i < histNumBins; i++) {
                int b = counts[i];
                sumCounts += b;
                sumCounts2 += b * b;
                int wb = counts[i] * i;
                weightedCount += wb;
            }
            meanCount = (float) sumCounts / histNumBins;
            if (sumCounts == 0) {
                meanBin = Float.NaN;
            } else {
                meanBin = (float) weightedCount / (float) sumCounts;
            }
            float norm = (histNumBins * (histNumBins - 1));
            stdCount = (float) Math.sqrt((float) (histNumBins * sumCounts2 - sumCounts * sumCounts) / norm);
            if (Float.isNaN(meanBin)) {
                stdBin = Float.NaN;
            } else {
                float sumSq = 0;
                for (int i = 0; i < histNumBins; i++) {
                    float dev = (i - meanBin);
                    float dev2 = dev * dev;
                    sumSq += counts[i] * dev2;
                }
                float varBin = sumSq / sumCounts;
                stdBin = (float) Math.sqrt(varBin);
            }
//            System.out.println("meanCount="+meanCount+"\tstdCount="+stdCount+"\tmeanBin="+meanBin+"\tstdBin="+stdBin+"\tstdCount="+stdCount);
        }

        synchronized float meanDeltaTimeUs() {
            return meanCount * histBinSizeUs;
        }

        synchronized float covBin() {
            if (Float.isNaN(meanBin)) {
                return Float.NaN;
            } else {
                return stdBin / meanBin;
            }
        }

        synchronized boolean proximityDetected() {
            if (sumCounts < countThreshold) {
                return false;
            }
            if (covBin() > maxCOV) {
                return false;
            }
            if (meanBin * histBinSizeUs > 5000) { // TODO make parameter
                return false;
            }
            return true;
        }
    }

    private class LEDSetterTask extends TimerTask {

        @Override
        public void run() {
            if (ledControl != null) {
                switch (ledControl.getLEDState(0)) {
                    case ON:
                        ledControl.setLEDState(0, HasLEDControl.LEDState.OFF);
                        break;
                    case OFF:
                    case UNKNOWN:
                    case FLASHING:
                        ledControl.setLEDState(0, HasLEDControl.LEDState.ON);
                }
                long ns = System.nanoTime();
                long dt = ns - lastCommandSentNs;
                lastCommandSentNs = ns;
//                log.info(dt / 1000 + " us since last command sent");
            }
        }
    }

    public boolean isProximityDetected() {
        return proximityDetected;
    }

    public void setProximityDetected(boolean yes) {
        boolean old = this.proximityDetected;
        this.proximityDetected = yes;
        getSupport().firePropertyChange(PROXIMITY, old, proximityDetected); // updates GUI among others
    }

    @Override
    public void resetFilter() {
        getEnclosedFilterChain().reset();
        histOn.reset();
        histOff.reset();
    }

    @Override
    public void initFilter() {
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            if (chip.getHardwareInterface() != null) {
                if (chip.getHardwareInterface() instanceof HasLEDControl) {
                    ledControl = (HasLEDControl) chip.getHardwareInterface();
                }
            }
            ledTimer = new Timer("LED Flasher");
            ledTimer.schedule(new LEDSetterTask(), 0, periodMs / 2);
        } else {
            if (ledTimer != null) {
                ledTimer.cancel();
            }
            if (ledControl != null) {
                ledControl.setLEDState(0, HasLEDControl.LEDState.OFF);
            }
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg instanceof HasLEDControl) {
            ledControl = (HasLEDControl) arg;
            ledControl.setLEDState(0, HasLEDControl.LEDState.OFF);
        }
    }
}
