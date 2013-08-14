package net.sf.jaer2.eventio.processors;

import javafx.scene.layout.Pane;

import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer2.eventio.ProcessorChain;
import net.sf.jaer2.eventio.eventpackets.EventPacketContainer;

public abstract class EventProcessorAnnotated extends EventProcessor {
	public EventProcessorAnnotated(final ProcessorChain chain) {
		super(chain);
	}

	protected abstract Object prepareAnnotateEvents(EventPacketContainer container);

	protected abstract void annotateEvents(EventPacketContainer container, Object annotateData,
		GLAutoDrawable glDrawable, Pane fxPane);

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			if (workQueue.drainTo(toProcess) == 0) {
				// No elements, retry.
				continue;
			}

			for (final EventPacketContainer container : toProcess) {
				if (processContainer(container)) {
					processEvents(container);

					// Annotation support.
					container.addToAnnotateDataSets(prepareAnnotateEvents(container));
				}

				if (nextProcessor != null) {
					nextProcessor.add(container);
				}
			}

			toProcess.clear();
		}
	}
}