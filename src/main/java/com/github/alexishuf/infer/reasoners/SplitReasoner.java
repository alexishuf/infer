package com.github.alexishuf.infer.reasoners;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Output the entailment of a model
 */
public interface SplitReasoner {
    @Nonnull Set<String> getProfiles();
    @Nonnull String getProfile();

    /**
     * Sets the profile. If null, restores the reasoner's default.
     */
    void setProfile(@Nullable String name) throws IllegalArgumentException;

    /**
     * Query echo flag. See isEchoEnabled().
     */
    boolean isEchoEnabled();

    /**
     * If echo is enabled, "inferences" in apply means original triples and inferred ones.
     * Default is false
     *
     * @param enabled new state for the echo flag
     */
    void setEchoEnabled(boolean enabled);

    /**
     * Places all inferences in the returned model.
     * @param main Source input model
     * @return
     */
    @Nonnull Model apply(@Nonnull Model main);

    /**
     * Output inferences, split into "background" and "main". This obeys the echo flag.
     *
     * @param inBackground Input background model, will not be modified.
     * @param inMain Input main model, will not be modified.
     * @param outBackground Receives all and only the inferred triples from inBackground
     * @param outMain Receives all and only the triples inferred by the union of inBackground
     *                and inMain that were not included in outBackground.
     * @see SplitReasoner
     */
    void apply(@Nonnull Model inBackground, @Nonnull Model inMain, @Nonnull Model outBackground,
               @Nonnull Model outMain);

    default  @Nonnull Model apply(@Nonnull Model inBackground, @Nonnull Model inMain,
                                @Nonnull Model outBackground) {
        Model outMain = ModelFactory.createDefaultModel();
        apply(inBackground, inMain, outBackground, outMain);
        return outMain;
    }
}
