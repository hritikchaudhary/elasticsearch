/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.plan.logical.join;

import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xpack.esql.core.expression.Attribute;
import org.elasticsearch.xpack.esql.core.expression.AttributeSet;
import org.elasticsearch.xpack.esql.core.expression.Expressions;
import org.elasticsearch.xpack.esql.core.tree.NodeInfo;
import org.elasticsearch.xpack.esql.core.tree.Source;
import org.elasticsearch.xpack.esql.plan.logical.BinaryPlan;
import org.elasticsearch.xpack.esql.plan.logical.ExecutesOn;
import org.elasticsearch.xpack.esql.plan.logical.LogicalPlan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.elasticsearch.xpack.esql.expression.NamedExpressions.mergeOutputAttributes;

/**
 * Coordinator-only INNER equi-join used to implement PromQL vector matching. The {@code Mapper}
 * turns it into the physical {@link org.elasticsearch.xpack.esql.plan.physical.EqJoinExec} when the
 * right ("build") side is materialized. It is an ephemeral node: it never crosses the wire and is
 * therefore not serialized (see {@link #writeTo}).
 * <p>
 * The left side is the "many"/probe side (its identity is preserved); the right side is the "one"/
 * build side. {@code addedFields} are the build columns copied into the result (value + copied
 * labels). {@code unique} selects 1:1 matching (at most one probe row per build row) vs many-to-one
 * ({@code group_left}/{@code group_right}).
 */
public class EqJoin extends BinaryPlan implements ExecutesOn.Coordinator {

    private final List<Attribute> leftFields;
    private final List<Attribute> rightFields;
    private final List<Attribute> addedFields;
    private final boolean unique;
    private List<Attribute> lazyOutput;

    public EqJoin(
        Source source,
        LogicalPlan left,
        LogicalPlan right,
        List<Attribute> leftFields,
        List<Attribute> rightFields,
        List<Attribute> addedFields,
        boolean unique
    ) {
        super(source, left, right);
        this.leftFields = leftFields;
        this.rightFields = rightFields;
        this.addedFields = addedFields;
        this.unique = unique;
    }

    public List<Attribute> leftFields() {
        return leftFields;
    }

    public List<Attribute> rightFields() {
        return rightFields;
    }

    public List<Attribute> addedFields() {
        return addedFields;
    }

    public boolean unique() {
        return unique;
    }

    @Override
    public List<Attribute> output() {
        if (lazyOutput == null) {
            List<Attribute> leftOutputWithoutKeys = left().output().stream().filter(attr -> leftFields.contains(attr) == false).toList();
            List<Attribute> rightWithAppendedKeys = new ArrayList<>(right().output());
            rightWithAppendedKeys.removeAll(rightFields);
            rightWithAppendedKeys.addAll(leftFields);
            lazyOutput = mergeOutputAttributes(rightWithAppendedKeys, leftOutputWithoutKeys);
        }
        return lazyOutput;
    }

    @Override
    public AttributeSet leftReferences() {
        return Expressions.references(leftFields);
    }

    @Override
    public AttributeSet rightReferences() {
        return Expressions.references(rightFields);
    }

    @Override
    protected AttributeSet computeReferences() {
        return Expressions.references(leftFields);
    }

    @Override
    public boolean expressionsResolved() {
        return true;
    }

    @Override
    public EqJoin replaceChildren(LogicalPlan left, LogicalPlan right) {
        return new EqJoin(source(), left, right, leftFields, rightFields, addedFields, unique);
    }

    @Override
    protected NodeInfo<EqJoin> info() {
        return NodeInfo.create(this, EqJoin::new, left(), right(), leftFields, rightFields, addedFields, unique);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        throw new UnsupportedOperationException("not serialized");
    }

    @Override
    public String getWriteableName() {
        throw new UnsupportedOperationException("not serialized");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (super.equals(o) == false) {
            return false;
        }
        EqJoin eqJoin = (EqJoin) o;
        return unique == eqJoin.unique
            && leftFields.equals(eqJoin.leftFields)
            && rightFields.equals(eqJoin.rightFields)
            && addedFields.equals(eqJoin.addedFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), leftFields, rightFields, addedFields, unique);
    }
}
