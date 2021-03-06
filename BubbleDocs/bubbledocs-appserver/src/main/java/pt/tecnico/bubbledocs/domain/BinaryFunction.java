package pt.tecnico.bubbledocs.domain;

import java.util.List;

import org.jdom2.Element;

import pt.tecnico.bubbledocs.exception.ImportDocumentException;
import pt.tecnico.bubbledocs.exception.ShouldNotExecuteException;

public class BinaryFunction extends BinaryFunction_Base {

    public BinaryFunction(Argument one, Argument two) {
        super();
        setFirstOperand(one);
        setSecondOperand(two);
    }

    BinaryFunction() {
        super();
    }

    @Override
    public void importFromXML(Element binElement) throws ImportDocumentException {
        try {
            Element first = binElement.getChild("firstOperand");
            Argument f = new Argument();

            List<Element> child = first.getChildren();
            for (Element el : child) {
                first = el;
                f = getType(el.getName());
            }

            f.importFromXML(first);
            setFirstOperand(f);

            Element second = binElement.getChild("secondOperand");
            Argument sc = new Argument();

            child = second.getChildren();
            for (Element el : child) {
                second = el;
                sc = getType(el.getName());
            }

            sc.importFromXML(second);
            setSecondOperand(sc);
        } catch (ImportDocumentException e) {
            throw new ImportDocumentException();
        }
    }

    public Argument getType(String str) {
        if (str.equals("literal"))
            return new Literal();
        else
            return new Reference();
    }

    public void delete() {
        setFirstOperand(null);
        setSecondOperand(null);
        super.delete();
    }

    public Integer getValue() {
        throw new ShouldNotExecuteException();
    }
}
