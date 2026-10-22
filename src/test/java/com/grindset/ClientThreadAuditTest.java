/*
 * Copyright (c) 2026, AoceanP <https://github.com/AoceanP>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.grindset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The sidebar runs on the Swing thread, and RuneLite only lets item prices, item compositions
 * and the game client be read on the client thread. Reading them from the sidebar makes the
 * plugin fail to start (Combat & XP Tracker 2.0 shipped with exactly that).
 *
 * This reads the plugin's source: every plugin method the sidebar calls (and the private
 * methods those call) must leave client-only calls inside clientThread.invoke.
 */
public class ClientThreadAuditTest
{
	private static final Path SOURCES = Paths.get("src/main/java/com/grindset");
	private static final List<String> SWING_FILES = Arrays.asList("GrindsetPanel.java");
	private static final List<String> CLIENT_ONLY = Arrays.asList("itemManager.getItemPrice", "itemManager.getItemComposition",
		"itemManager.getItemStats", "client.get", "client.set", "npcManager.", "maxHitCalculator.");
	private static final Pattern CLIENT_THREAD_BLOCK = Pattern.compile(
		"clientThread\\.invoke(Later)?\\(\\(\\) ->\\s*\\{.*?\\n\\t\\t\\}\\);", Pattern.DOTALL);
	private static final Pattern CLIENT_THREAD_CALL = Pattern.compile("clientThread\\.invoke(Later)?\\([^;]*\\);");

	private String plugin;

	@Test
	public void theSidebarNeverReadsTheGameOffTheClientThread() throws IOException
	{
		plugin = read("GrindsetPlugin.java");
		Set<String> called = new TreeSet<>();
		Pattern call = Pattern.compile("plugin\\.(\\w+)\\(");
		for (String file : SWING_FILES)
		{
			Path path = SOURCES.resolve(file);
			if (Files.exists(path))
			{
				Matcher m = call.matcher(read(file));
				while (m.find())
				{
					called.add(m.group(1));
				}
			}
		}
		assertTrue("the sidebar should call into the plugin", called.size() >= 5);

		List<String> problems = new ArrayList<>();
		for (String method : called)
		{
			String body = offClientThread(body(method));
			check(method, body, problems);
			// One level down: private helpers it calls directly.
			Matcher helper = Pattern.compile("\\b([a-z]\\w+)\\(").matcher(body);
			while (helper.find())
			{
				String name = helper.group(1);
				if (!name.equals(method) && !called.contains(name))
				{
					check(method + " -> " + name, offClientThread(body(name)), problems);
				}
			}
		}
		assertTrue("Client-only calls reached from the sidebar (wrap them in clientThread.invoke): " + problems,
			problems.isEmpty());
	}

	private static void check(String where, String body, List<String> problems)
	{
		for (String bad : CLIENT_ONLY)
		{
			if (body.contains(bad))
			{
				problems.add(where + " calls " + bad);
			}
		}
	}

	private static String offClientThread(String body)
	{
		String s = CLIENT_THREAD_BLOCK.matcher(body).replaceAll("");
		return CLIENT_THREAD_CALL.matcher(s).replaceAll("");
	}

	/**
	 * @return the bodies of every method with this name in the plugin
	 */
	private String body(String name)
	{
		StringBuilder out = new StringBuilder();
		Matcher m = Pattern.compile("\\n\\t(?:public |private |protected |static |synchronized |final )*[\\w<>.,\\[\\] ]+\\s"
			+ Pattern.quote(name) + "\\([^)]*\\)\\s*\\{").matcher(plugin);
		while (m.find())
		{
			int depth = 1;
			int j = m.end();
			while (depth > 0 && j < plugin.length())
			{
				char c = plugin.charAt(j);
				if (c == '{')
				{
					depth++;
				}
				else if (c == '}')
				{
					depth--;
				}
				j++;
			}
			out.append(plugin, m.end(), j).append('\n');
		}
		return out.toString();
	}

	private static String read(String file) throws IOException
	{
		return new String(Files.readAllBytes(SOURCES.resolve(file)), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}
}
