package pt.tecnico.bubbledocs.service;

import pt.tecnico.bubbledocs.domain.Cell;
import pt.tecnico.bubbledocs.domain.Reference;
import pt.tecnico.bubbledocs.domain.Spreadsheet;
import pt.tecnico.bubbledocs.domain.User;
import pt.tecnico.bubbledocs.exception.BubbleDocsException;
import pt.tecnico.bubbledocs.exception.UnauthorizedOperationException;
import pt.tecnico.bubbledocs.exception.UserNotInSessionException;

public class AssignReferenceCell extends BubbleDocsService {
    private int docId;
    
    private String result;
    private String cellId;
    private String userToken;
    private String reference;

    public AssignReferenceCell(String userToken, int docId, String cellId, String reference) {
        this.userToken = userToken;
        this.docId = docId;
        this.cellId = cellId;
        this.reference = reference;
    }

    @Override
    protected void dispatch() throws BubbleDocsException {
        User user = getUserByToken(userToken);

        if (!isLoggedIn(user))
            throw new UserNotInSessionException();

        Spreadsheet doc = getBubbledocs().getSpreadsheet(docId);
        
        if (doc.getCreator().equals(user) || doc.isWriter(user.getUsername())) {
            String[] cellCoordinates = cellId.split(";");
            int cellRow = Integer.parseInt(cellCoordinates[0]);
            int cellColumn = Integer.parseInt(cellCoordinates[1]);

            String[] referenceCoordinates = reference.split(";");
            int referenceRow = Integer.parseInt(referenceCoordinates[0]);
            int referenceColumn = Integer.parseInt(referenceCoordinates[1]);
            
            Cell reference = doc.getCell(referenceRow, referenceColumn);

            Reference ref = new Reference(reference);
            doc.addCellContent(cellRow, cellColumn, ref);
     
            Integer res = doc.getCell(cellRow, cellColumn).getContent().getValue();
            
            if (res == null)
                this.result = null;
            else
                this.result = res.toString();
        } else
            throw new UnauthorizedOperationException();
    }

    public String getResult() {
        return this.result;
    }


}
