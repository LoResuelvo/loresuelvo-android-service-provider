// Return source line numbers for real tag lines, excluding descriptions and doc strings.
export function wipTagLines(source) {
  const lines = [];
  let docString = null;
  source.split(/\r?\n/).forEach((raw, index) => {
    const line = raw.trim();
    if (docString) {
      if (line === docString) docString = null;
      return;
    }
    const delimiter = line.match(/^("""|```)/);
    if (delimiter) {
      docString = delimiter[1];
    } else if (line.startsWith("@") && line.split(/\s+/).includes("@wip")) {
      lines.push(index + 1);
    }
  });
  return lines;
}
