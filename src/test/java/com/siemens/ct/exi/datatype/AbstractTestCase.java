/*
 * Copyright (c) 2007-2016 Siemens AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * 
 */

package com.siemens.ct.exi.datatype;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import junit.framework.TestCase;

import com.siemens.ct.exi.io.channel.BitDecoderChannel;
import com.siemens.ct.exi.io.channel.BitEncoderChannel;
import com.siemens.ct.exi.io.channel.ByteDecoderChannel;
import com.siemens.ct.exi.io.channel.ByteEncoderChannel;
import com.siemens.ct.exi.io.channel.DecoderChannel;
import com.siemens.ct.exi.io.channel.EncoderChannel;

public abstract class AbstractTestCase extends TestCase {

	private ByteArrayOutputStream bitBaos;
	private ByteArrayOutputStream baos;

	public AbstractTestCase() {
	}

	public AbstractTestCase(String name) {
		super(name);
	}

	/*
	 * Bit - Mode
	 */
	protected OutputStream getBitOutputStream() {
		bitBaos = new ByteArrayOutputStream();
		return bitBaos;
	}

	protected InputStream getBitInputStream() throws IOException {
		bitBaos.flush();
		return new ByteArrayInputStream(bitBaos.toByteArray());
	}

	protected EncoderChannel getBitEncoder() {
		return new BitEncoderChannel(getBitOutputStream());
	}

	protected DecoderChannel getBitDecoder() throws IOException {
		return new BitDecoderChannel(getBitInputStream());
	}

	/*
	 * Byte - Mode
	 */
	protected EncoderChannel getByteEncoder() {
		this.baos = new ByteArrayOutputStream();
		return new ByteEncoderChannel(baos);
	}

	protected DecoderChannel getByteDecoder() throws IOException {
		return new ByteDecoderChannel(new ByteArrayInputStream(
				baos.toByteArray()));
	}

}
