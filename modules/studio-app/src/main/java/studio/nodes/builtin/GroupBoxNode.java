package studio.nodes.builtin;

import studio.graph.AbstractNode;
import studio.graph.Frame;
import studio.graph.Parameter;

/**
 * Annotation rectangle that groups a region of the canvas with a title and
 * tint. Has no ports; the runtime skips it. The editor draws it behind
 * everything and only the title bar / borders are clickable, so clicks in
 * the body fall through to underlying nodes.
 */
public final class GroupBoxNode extends AbstractNode {

    public static final String TYPE_ID = "studio.builtin.GroupBox";

    public final Parameter<String>  pText;
    public final Parameter<Integer> pWidth;
    public final Parameter<Integer> pHeight;
    public final Parameter<float[]> pColor;

    public GroupBoxNode() {
        super();
        this.pText   = declareParam(Parameter.text("text", "Group"));
        this.pWidth  = declareParam(Parameter.intRange("width",  320, 80, 4096));
        this.pHeight = declareParam(Parameter.intRange("height", 200, 60, 4096));
        // RGBA tint — alpha low so the fill stays translucent over the grid.
        Parameter<float[]> c = Parameter.vec4("color", new float[]{ 0.50f, 0.65f, 0.95f, 0.18f });
        c.uiHint = Parameter.UiHint.COLOR_RGBA;
        this.pColor = declareParam(c);
        setLabel("Group");
    }

    @Override public String typeId() { return TYPE_ID; }

    @Override public void evaluate(Frame frame) {
        // No-op — group boxes are documentation, not graph nodes.
    }
}
