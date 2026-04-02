package nl.inl.blacklab.plugins;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocTask;

/** Performs a task on a document in a BlackLab index. */
public abstract class DocTaskType extends Plugin {

    /**
     * Instantiate doc task.
     *
     * @param index index to instantiate the task for
     * @param args arguments for the doc task
     * @return doc task
     */
    public abstract DocTask docTask(BlackLabIndex index, Map<String, String> args);
}
