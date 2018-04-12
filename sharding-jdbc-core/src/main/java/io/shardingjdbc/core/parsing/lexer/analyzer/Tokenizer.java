/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingjdbc.core.parsing.lexer.analyzer;

import io.shardingjdbc.core.parsing.lexer.token.DefaultKeyword;
import io.shardingjdbc.core.parsing.lexer.token.Literals;
import io.shardingjdbc.core.parsing.lexer.token.Symbol;
import io.shardingjdbc.core.parsing.lexer.token.Token;
import io.shardingjdbc.core.parsing.lexer.token.TokenType;
import lombok.RequiredArgsConstructor;

/**
 * Tokenizer. token解释器
 *
 * @author zhangliang
 */
@RequiredArgsConstructor
public final class Tokenizer {
    
	/**
	 * mysql特有单行注释#标记字符长度
	 */
    private static final int MYSQL_SPECIAL_COMMENT_BEGIN_SYMBOL_LENGTH = 1;
    /**
     * 单行注释标记字符长度：//、--
     */
    private static final int COMMENT_BEGIN_SYMBOL_LENGTH = 2;
    /**
     * hint标记长度
     */
    private static final int HINT_BEGIN_SYMBOL_LENGTH = 3;
    /**
     * 注释以及hint结束标记长度
     */
    private static final int COMMENT_AND_HINT_END_SYMBOL_LENGTH = 2;
    /**
     * 16进制标记长度，ox
     */
    private static final int HEX_BEGIN_SYMBOL_LENGTH = 2;
    
    /**
     * 待解释完整sql字符串（如：select * from table_1 where name like '%sf%';)
     */
    private final String input;
    /**
     * 数据库定义词法标记类型字典
     */
    private final Dictionary dictionary;
    /**
     * 当前待解释开始位置
     */
    private final int offset;
    
    /**
     * skip whitespace. 跳过空格
     * 
     * @return offset after whitespace skipped 
     */
    public int skipWhitespace() {
        int length = 0;
        while (CharType.isWhitespace(charAt(offset + length))) {
            length++;
        }
        return offset + length;
    }
    
    /**
     * skip comment. 跳过注释内容行
     * 
     * @return offset after comment skipped
     */
    public int skipComment() {
        char current = charAt(offset);
        char next = charAt(offset + 1);
        if (isSingleLineCommentBegin(current, next)) {
            return skipSingleLineComment(COMMENT_BEGIN_SYMBOL_LENGTH);
        } else if ('#' == current) {//mysql特有的注释风格
            return skipSingleLineComment(MYSQL_SPECIAL_COMMENT_BEGIN_SYMBOL_LENGTH);
        } else if (isMultipleLineCommentBegin(current, next)) {
            return skipMultiLineComment();
        }
        return offset;
    }
    
    /**
     * 
     * 功能描述: 检查是否单行注释开头<br>
     * @date: 2018年4月12日 下午2:53:04 
     * @param ch 当前字符
     * @param next 下一个字符
     * @return
     */
    private boolean isSingleLineCommentBegin(final char ch, final char next) {
        return '/' == ch && '/' == next || '-' == ch && '-' == next;
    }
    
    /**
     * 
     * 功能描述: 获取待跳过的单行注释长度<br>
     * @date: 2018年4月12日 下午2:50:32 
     * @param commentSymbolLength
     * @return int 单行注释内容长度
     */
    private int skipSingleLineComment(final int commentSymbolLength) {
        int length = commentSymbolLength;
        //计算本行注释长度
        while (!CharType.isEndOfInput(charAt(offset + length)) && '\n' != charAt(offset + length)) {
            length++;
        }
        return offset + length + 1;
    }
    
    /**
     * 
     * 功能描述: 检查是否多行注释<br>
     * @date: 2018年4月12日 下午3:02:47 
     * @param ch 当前字符
     * @param next 下一个字符
     * @return
     */
    private boolean isMultipleLineCommentBegin(final char ch, final char next) {
        return '/' == ch && '*' == next;
    }
    
    /**
     * 
     * 功能描述: 跳过多行注释内容<br>
     * @author: wenwj
     * @date: 2018年4月12日 下午3:03:16 
     * @version 1.5.0
     * @return
     */
    private int skipMultiLineComment() {
        return untilCommentAndHintTerminateSign(COMMENT_BEGIN_SYMBOL_LENGTH);
    }
    
    /**
     * skip hint. 跳过hint内容
     *
     * @return offset after hint skipped
     */
    public int skipHint() {
        return untilCommentAndHintTerminateSign(HINT_BEGIN_SYMBOL_LENGTH);
    }
    
    /**
     * 返回待跳过的注意及hint内容长度
     */
    private int untilCommentAndHintTerminateSign(final int beginSymbolLength) {
        int length = beginSymbolLength;
        while (!isMultipleLineCommentEnd(charAt(offset + length), charAt(offset + length + 1))) {
            if (CharType.isEndOfInput(charAt(offset + length))) {
                throw new UnterminatedCharException("*/");
            }
            length++;
        }
        return offset + length + COMMENT_AND_HINT_END_SYMBOL_LENGTH;
    }
    /**
     * 
     * 功能描述: 检查是否多行注释结尾<br>
     * @date: 2018年4月12日 下午3:05:05 
     * @param ch
     * @param next
     * @return
     */
    private boolean isMultipleLineCommentEnd(final char ch, final char next) {
        return '*' == ch && '/' == next;
    }
    
    /**
	* 扫描变量.
	* 在 MySQL 里，@代表用户变量；@@代表系统变量。
	* 在 SQLServer 里，有 @@。
	*
     * @return variable token 变量类型
     */
    public Token scanVariable() {
        int length = 1;
        if ('@' == charAt(offset + 1)) {
            length++;
        }
        while (isVariableChar(charAt(offset + length))) {
            length++;
        }
        return new Token(Literals.VARIABLE, input.substring(offset, offset + length), offset + length);
    }
    
    /**
     * 
     * 功能描述: 检查是否以下范围内字符： A-Z,a-z,0-9,_,$,#,.<br>
     * @date: 2018年4月12日 下午3:51:40 
     * @param ch
     * @return
     */
    private boolean isVariableChar(final char ch) {
        return isIdentifierChar(ch) || '.' == ch;
    }
    
    /**
     * scan identifier.扫描标识符.
     *
     * @return identifier token 标识符标记
     */
    public Token scanIdentifier() {
        if ('`' == charAt(offset)) {
            int length = getLengthUntilTerminatedChar('`');
            return new Token(Literals.IDENTIFIER, input.substring(offset, offset + length), offset + length);
        }
        int length = 0;
        while (isIdentifierChar(charAt(offset + length))) {
            length++;
        }
        String literals = input.substring(offset, offset + length);
        if (isAmbiguousIdentifier(literals)) {
            return new Token(processAmbiguousIdentifier(offset + length, literals), literals, offset + length);
        }
        return new Token(dictionary.findTokenType(literals, Literals.IDENTIFIER), literals, offset + length);
    }
    /**
     * 
     * 功能描述: 往后查找指定关键词，获取中间字符集长度<br>
     * @date: 2018年4月12日 下午2:21:38 
     * @param terminatedChar
     * @return
     */
    private int getLengthUntilTerminatedChar(final char terminatedChar) {
        int length = 1;
        while (terminatedChar != charAt(offset + length) || hasEscapeChar(terminatedChar, offset + length)) {
            if (offset + length >= input.length()) {
                throw new UnterminatedCharException(terminatedChar);
            }
            if (hasEscapeChar(terminatedChar, offset + length)) {
                length++;
            }
            length++;
        }
        return length + 1;
    }
    
    /**
     * 
     * 功能描述: 检查接下来两个字符是否相同关键词，检查是否转义字符<br>
     * @date: 2018年4月12日 下午2:20:23 
     * @param charIdentifier
     * @param offset
     * @return
     */
    private boolean hasEscapeChar(final char charIdentifier, final int offset) {
        return charIdentifier == charAt(offset) && charIdentifier == charAt(offset + 1);
    }
    
    /**
     * 
     * 功能描述:检查是否以下范围内字符： A-Z，a-z,0-9,_,$,#<br>
     * @date: 2018年4月12日 下午3:56:20 
     * @param ch 待检查字符
     * @return true，是，false，否
     */
    private boolean isIdentifierChar(final char ch) {
        return CharType.isAlphabet(ch) || CharType.isDigital(ch) || '_' == ch || '$' == ch || '#' == ch;
    }
    
    /**
     * 
     * 功能描述: 检查是否order、group字符串<br>
     * @date: 2018年4月12日 下午3:59:58 
     * @param literals
     * @return
     */
    private boolean isAmbiguousIdentifier(final String literals) {
        return DefaultKeyword.ORDER.name().equalsIgnoreCase(literals) || DefaultKeyword.GROUP.name().equalsIgnoreCase(literals);
    }
    /**
     * 
     * 功能描述: 处理order是否关键词，检查后面是否紧随by，获取引起歧义的标识符对应的词法标记类型<br>
     * @date: 2018年4月12日 下午4:00:42 
     * @param offset
     * @param literals
     * @return
     */
    private TokenType processAmbiguousIdentifier(final int offset, final String literals) {
        int i = 0;
        while (CharType.isWhitespace(charAt(offset + i))) {
            i++;
        }
        if (DefaultKeyword.BY.name().equalsIgnoreCase(String.valueOf(new char[] {charAt(offset + i), charAt(offset + i + 1)}))) {
            return dictionary.findTokenType(literals);
        }
        return Literals.IDENTIFIER;
    }
    
    /**
     * scan hex decimal.扫描16进制
     *
     * @return hex decimal token
     */
    public Token scanHexDecimal() {
        int length = HEX_BEGIN_SYMBOL_LENGTH;
        if ('-' == charAt(offset + length)) {//负数
            length++;
        }
        while (isHex(charAt(offset + length))) {
            length++;
        }
        return new Token(Literals.HEX, input.substring(offset, offset + length), offset + length);
    }
    
    /**
     * 
     * 功能描述: 检查是否16进制规范字符或数字<br>
     * @date: 2018年4月12日 下午4:04:54 
     * @param ch
     * @return
     */
    private boolean isHex(final char ch) {
        return ch >= 'A' && ch <= 'F' || ch >= 'a' && ch <= 'f' || CharType.isDigital(ch);
    }
    
    /**
     * scan number.扫描数字
     *
     * @return number token
     */
    public Token scanNumber() {
        int length = 0;
        if ('-' == charAt(offset + length)) {//负数
            length++;
        }
        length += getDigitalLength(offset + length);
        boolean isFloat = false;
        if ('.' == charAt(offset + length)) {//获取小数及小数位数，并累计长度
            isFloat = true;
            length++;
            length += getDigitalLength(offset + length);
        }
        if (isScientificNotation(offset + length)) {//获取科学计数法，总计长度
            isFloat = true;
            length++;
            if ('+' == charAt(offset + length) || '-' == charAt(offset + length)) {
                length++;
            }
            length += getDigitalLength(offset + length);
        }
        if (isBinaryNumber(offset + length)) { //浮点数标记
            isFloat = true;
            length++;
        }
        return new Token(isFloat ? Literals.FLOAT : Literals.INT, input.substring(offset, offset + length), offset + length);
    }
    /**
     * 
     * 功能描述: 获取连续的数字长度<br>
     * @date: 2018年4月12日 下午4:07:43 
     * @param offset 开始位置
     * @return
     */
    private int getDigitalLength(final int offset) {
        int result = 0;
        while (CharType.isDigital(charAt(offset + result))) {
            result++;
        }
        return result;
    }
    /**
     * 
     * 功能描述: 是否科学计数<br>
     * @date: 2018年4月12日 下午4:11:41 
     * @param offset
     * @return
     */
    private boolean isScientificNotation(final int offset) {
        char current = charAt(offset);
        return 'e' == current || 'E' == current;
    }
    
    /**
     * 
     * 功能描述: 是否浮点数<br>
     * @date: 2018年4月12日 下午4:11:53 
     * @param offset
     * @return
     */
    private boolean isBinaryNumber(final int offset) {
        char current = charAt(offset);
        return 'f' == current || 'F' == current || 'd' == current || 'D' == current;
    }
    
    /**
     * scan chars.扫描字符列
     *
     * @return chars token
     */
    public Token scanChars() {
        return scanChars(charAt(offset));
    }
    
    private Token scanChars(final char terminatedChar) {
        int length = getLengthUntilTerminatedChar(terminatedChar);
        return new Token(Literals.CHARS, input.substring(offset + 1, offset + length - 1), offset + length);
    }
    
    /**
     * scan symbol. 扫描标记列
     *
     * @return symbol token
     */
    public Token scanSymbol() {
        int length = 0;
        while (CharType.isSymbol(charAt(offset + length))) {
            length++;
        }
        String literals = input.substring(offset, offset + length);
     // 倒序遍历，查询符合条件的 符号。例如 literals = ";;"，会是拆分成两个 ";"。如果基于正序，literals = "<="，会被解析成 "<" + "="。
        Symbol symbol;
        while (null == (symbol = Symbol.literalsOf(literals))) {
            literals = input.substring(offset, offset + --length);
        }
        return new Token(symbol, literals, offset + length);
    }
    
    /**
     * 
     * 功能描述: 获取对应位置字符，超出字符串长度，则返回结束符，反之正常返回<br>
     * @date: 2018年4月12日 下午4:58:45 
     * @param index
     * @return
     */
    private char charAt(final int index) {
        return index >= input.length() ? (char) CharType.EOI : input.charAt(index);
    }
}
