package com.salesforce.phoenix.compile;

import java.sql.SQLException;

import com.salesforce.phoenix.parse.ColumnParseNode;
import com.salesforce.phoenix.parse.FamilyWildcardParseNode;
import com.salesforce.phoenix.parse.ParseNode;
import com.salesforce.phoenix.parse.ParseNodeFactory;
import com.salesforce.phoenix.parse.ParseNodeRewriter;
import com.salesforce.phoenix.parse.SelectStatement;
import com.salesforce.phoenix.parse.WildcardParseNode;
import com.salesforce.phoenix.schema.ColumnRef;
import com.salesforce.phoenix.schema.PDataType;
import com.salesforce.phoenix.util.IndexUtil;

public class IndexStatementRewriter extends ParseNodeRewriter {
    private static final ParseNodeFactory FACTORY = new ParseNodeFactory();
    
    public IndexStatementRewriter(ColumnResolver dataResolver) {
        super(dataResolver);
    }
    
    /**
     * Rewrite the select statement by translating all data table column references to
     * references to the corresponding index column.
     * @param statement the select statement
     * @return new select statement or the same one if nothing was rewritten.
     * @throws SQLException 
     */
    public static SelectStatement translate(SelectStatement statement, ColumnResolver dataResolver) throws SQLException {
        return rewrite(statement, new IndexStatementRewriter(dataResolver));
    }

    @Override
    public ParseNode visit(ColumnParseNode node) throws SQLException {
        ColumnRef dataColRef = getResolver().resolveColumn(node.getSchemaName(), node.getTableName(), node.getName());
        String indexColName = IndexUtil.getIndexColumnName(dataColRef.getColumn());
        // Same alias as before, but use the index column name instead of the data column name
        ParseNode indexColNode = new ColumnParseNode(null, indexColName, node.toString());
        PDataType indexColType = IndexUtil.getIndexColumnDataType(dataColRef.getColumn());
        PDataType dataColType = dataColRef.getColumn().getDataType();

        // Coerce index column reference back to same type as data column so that
        // expression behave exactly the same. No need to invert, as this will be done
        // automatically as needed.
        if (!indexColType.isBytesComparableWith(dataColType)) {
            indexColNode = FACTORY.cast(indexColNode, dataColType);
        }
        return indexColNode;
    }

    @Override
    public ParseNode visit(WildcardParseNode node) throws SQLException {
        return WildcardParseNode.REWRITE_INSTANCE;
    }

    @Override
    public ParseNode visit(FamilyWildcardParseNode node) throws SQLException {
        return new FamilyWildcardParseNode(node, true);
    }
    
}
