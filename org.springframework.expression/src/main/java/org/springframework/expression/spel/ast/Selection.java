/*
 * Copyright 2002-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.expression.spel.ast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.runtime.Token;
import org.springframework.core.convert.ConversionContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelException;
import org.springframework.expression.spel.SpelMessages;

/**
 * Represents selection over a map or collection. For example: {1,2,3,4,5,6,7,8,9,10}.?{#isEven(#this) == 'y'} returns
 * [2, 4, 6, 8, 10]
 * 
 * Basically a subset of the input data is returned based on the evaluation of the expression supplied as selection
 * criteria.
 * 
 * @author Andy Clement
 */
public class Selection extends SpelNodeImpl {

	public final static int ALL = 0; // ?[]
	public final static int FIRST = 1; // ^[]
	public final static int LAST = 2; // $[]

	private final int variant;

	public Selection(Token payload, int variant) {
		super(payload);
		this.variant = variant;
	}

	@Override
	public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
		TypedValue op = state.getActiveContextObject();
		Object operand = op.getValue();
		
		SpelNodeImpl selectionCriteria = getChild(0);
		if (operand instanceof Map) {
			Map<?, ?> mapdata = (Map<?, ?>) operand;
			// TODO don't lose generic info for the new map
			Map<Object,Object> result = new HashMap<Object,Object>();
			Object lastKey = null;
			for (Map.Entry entry : mapdata.entrySet()) {
				try {
					lastKey = entry.getKey();
					TypedValue kvpair = new TypedValue(entry,ConversionContext.valueOf(Map.Entry.class));
					state.pushActiveContextObject(kvpair);
					Object o = selectionCriteria.getValueInternal(state).getValue();
					if (o instanceof Boolean) {
						if (((Boolean) o).booleanValue() == true) {
							if (variant == FIRST) {
								result.put(entry.getKey(),entry.getValue());
								return new TypedValue(result);
							}
							result.put(entry.getKey(),entry.getValue());
						}
					} else {
						throw new SpelException(selectionCriteria.getCharPositionInLine(),
								SpelMessages.RESULT_OF_SELECTION_CRITERIA_IS_NOT_BOOLEAN);// ,selectionCriteria.stringifyAST());
					}
				} finally {
					state.popActiveContextObject();
				}
			}
			if ((variant == FIRST || variant == LAST) && result.size() == 0) {
				return new TypedValue(null,ConversionContext.NULL);
			}
			if (variant == LAST) {
				Map resultMap = new HashMap();
				Object lastValue = result.get(lastKey);
				resultMap.put(lastKey,lastValue);
				return new TypedValue(resultMap,ConversionContext.valueOf(Map.class));
			}
			return new TypedValue(result,op.getTypeDescriptor());
		} else if (operand instanceof Collection) {
			List<Object> data = new ArrayList<Object>();
			data.addAll((Collection<?>) operand);
			List<Object> result = new ArrayList<Object>();
			int idx = 0;
			for (Object element : data) {
				try {
					state.pushActiveContextObject(new TypedValue(element,ConversionContext.valueOf(op.getTypeDescriptor().getElementType())));
					state.enterScope("index", idx);
					Object o = selectionCriteria.getValueInternal(state).getValue();
					if (o instanceof Boolean) {
						if (((Boolean) o).booleanValue() == true) {
							if (variant == FIRST) {
								return new TypedValue(element,ConversionContext.valueOf(op.getTypeDescriptor().getElementType()));
							}
							result.add(element);
						}
					} else {
						throw new SpelException(selectionCriteria.getCharPositionInLine(),
								SpelMessages.RESULT_OF_SELECTION_CRITERIA_IS_NOT_BOOLEAN);// ,selectionCriteria.stringifyAST());
					}
					idx++;
				} finally {
					state.exitScope();
					state.popActiveContextObject();
				}
			}
			if ((variant == FIRST || variant == LAST) && result.size() == 0) {
				return TypedValue.NULL_TYPED_VALUE;
			}
			if (variant == LAST) {
				return new TypedValue(result.get(result.size() - 1),ConversionContext.valueOf(op.getTypeDescriptor().getElementType()));
			}
			return new TypedValue(result,op.getTypeDescriptor());
		} else {
			throw new SpelException(getCharPositionInLine(), SpelMessages.INVALID_TYPE_FOR_SELECTION,
					(operand == null ? "null" : operand.getClass().getName()));
		}
	}

	@Override
	public String toStringAST() {
		StringBuilder sb = new StringBuilder();
		switch (variant) {
		case ALL:
			sb.append("?[");
			break;
		case FIRST:
			sb.append("^[");
			break;
		case LAST:
			sb.append("$[");
			break;
		}
		return sb.append(getChild(0).toStringAST()).append("]").toString();
	}

}
