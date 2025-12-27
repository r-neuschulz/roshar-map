const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const parseSections = require('../build/loaders/parse-markdown-sections');

const LANG_DIR = path.resolve(__dirname, '../src/lang');
const JAVA_FILE = path.resolve(__dirname, '../src/lang/search/al-wo.java');
const EN_US_FILE = path.join(LANG_DIR, 'en-US.lang.json');
const AL_WO_FILE = path.join(LANG_DIR, 'al-wo.lang.json');
const TRANSLATIONS_DIR = path.resolve(__dirname, '../translations');
const EN_US_TRANSLATIONS = path.join(TRANSLATIONS_DIR, 'en-US');
const AL_WO_TRANSLATIONS = path.join(TRANSLATIONS_DIR, 'al-wo');

async function transliterateString(text) {
    if (!text || text.trim().length === 0) {
        return text;
    }
    
    // Compile Java file if needed (only once)
    const javaDir = path.dirname(JAVA_FILE);
    const className = 'AlethiTransliterator_1_9_5_2';
    const classFile = path.join(javaDir, `${className}.class`);
    
    if (!global.javaCompiled) {
        if (!fs.existsSync(classFile) || 
            fs.statSync(JAVA_FILE).mtime > fs.statSync(classFile).mtime) {
            console.log(`Compiling ${path.basename(JAVA_FILE)}...`);
            execSync(`javac "${JAVA_FILE}"`, { stdio: 'inherit', cwd: javaDir });
        }
        global.javaCompiled = true;
    }
    
    return new Promise((resolve, reject) => {
        const { spawn } = require('child_process');
        
        const proc = spawn('java', ['-Xmx512m', '-cp', javaDir, className, '--string'], {
            cwd: javaDir,
            stdio: ['pipe', 'pipe', 'pipe']
        });
        
        let stdout = '';
        let stderr = '';
        
        proc.stdout.on('data', (data) => {
            stdout += data.toString('utf8');
        });
        
        proc.stderr.on('data', (data) => {
            stderr += data.toString('utf8');
        });
        
        proc.on('error', (error) => {
            reject(error);
        });
        
        // Set timeout
        const timeout = setTimeout(() => {
            proc.kill('SIGTERM');
            reject(new Error('Transliteration timed out after 30 seconds'));
        }, 30000);
        
        proc.on('close', (code) => {
            clearTimeout(timeout);
            
            // Force cleanup
            proc.kill('SIGKILL');
            
            if (code !== 0 && code !== null) {
                reject(new Error(stderr || `Process exited with code ${code}`));
                return;
            }
            
            if (!stdout || stdout.trim().length === 0) {
                resolve(text); // Return original on empty output
                return;
            }
            
            resolve(stdout.trim());
        });
        
        // Write input and close stdin
        proc.stdin.write(text, 'utf8');
        proc.stdin.end();
    });
}

async function transliterateStrings(strings, label = 'strings') {
    console.log(`Transliterating ${strings.length} ${label}...`);
    const results = [];
    const startTime = Date.now();
    
    // Process strings one at a time with small delays to allow GC
    for (let i = 0; i < strings.length; i++) {
        try {
            const result = await transliterateString(strings[i]);
            results.push(result);
            
            // Small delay every 10 strings to allow garbage collection
            if ((i + 1) % 10 === 0) {
                await new Promise(resolve => setImmediate(resolve));
            }
            
            // Show progress every 10 strings or at milestones
            if ((i + 1) % 10 === 0 || i === 0 || i === strings.length - 1) {
                const percent = Math.round(((i + 1) / strings.length) * 100);
                const elapsed = Date.now() - startTime;
                const avgTime = elapsed / (i + 1);
                const remaining = Math.round((strings.length - i - 1) * avgTime / 1000);
                process.stdout.write(`\r  Progress: ${i + 1}/${strings.length} (${percent}%)${remaining > 0 ? ` - ~${remaining}s remaining` : ''}   `);
            }
        } catch (error) {
            console.error(`\nError processing string ${i + 1}: ${error.message}`);
            results.push(strings[i]); // Use original on error
        }
    }
    
    // Clear the progress line and show completion
    process.stdout.write('\r');
    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`  ✓ Completed ${strings.length} ${label} in ${elapsed}s`);
    
    return results;
}

async function main() {
    console.log('=== Generating al-wo language files ===\n');
    
    // Step 1: Process en-US.lang.json
    console.log('Step 1: Processing en-US.lang.json...');
    const step1StartTime = Date.now();
    const enData = JSON.parse(fs.readFileSync(EN_US_FILE, 'utf8'));
    
    const strings = [];
    collectStrings(enData, strings);
    
    console.log(`Found ${strings.length} strings in language file.`);
    
    const translatedStrings = await transliterateStrings(strings, 'language strings');
    
    // Reconstruct JSON
    const alWoData = reconstruct(enData, translatedStrings);
    
    // Update metadata
    alWoData['name'] = "Alethi";
    alWoData['texture-locale'] = "en-US";
    alWoData['search-language'] = "en-US";
    
    // Ensure output directory exists
    fs.mkdirSync(path.dirname(AL_WO_FILE), { recursive: true });
    fs.writeFileSync(AL_WO_FILE, JSON.stringify(alWoData, null, 4));
    const step1Elapsed = ((Date.now() - step1StartTime) / 1000).toFixed(1);
    console.log(`✓ Generated ${path.basename(AL_WO_FILE)} (Step 1 completed in ${step1Elapsed}s)\n`);
    
    // Step 2: Process translation markdown files
    console.log('Step 2: Processing translation markdown files...');
    const step2StartTime = Date.now();
    
    const types = ['events', 'locations', 'characters', 'misc'];
    let totalFiles = 0;
    
    // Count total files first for overall progress
    let totalFilesCount = 0;
    for (const type of types) {
        const sourceDir = path.join(EN_US_TRANSLATIONS, type);
        if (fs.existsSync(sourceDir)) {
            const files = fs.readdirSync(sourceDir).filter(f => f.endsWith('.md'));
            totalFilesCount += files.length;
        }
    }
    
    console.log(`  Total files to process: ${totalFilesCount}\n`);
    
    for (const type of types) {
        const sourceDir = path.join(EN_US_TRANSLATIONS, type);
        const targetDir = path.join(AL_WO_TRANSLATIONS, type);
        
        if (!fs.existsSync(sourceDir)) {
            console.log(`  Skipping ${type} (directory not found)`);
            return;
        }
        
        // Ensure target directory exists
        fs.mkdirSync(targetDir, { recursive: true });
        
        const files = fs.readdirSync(sourceDir)
            .filter(f => f.endsWith('.md'))
            .sort();
        
        console.log(`  Processing ${type} (${files.length} files)...`);
        const typeStartTime = Date.now();
        
        for (let index = 0; index < files.length; index++) {
            const fileName = files[index];
            const sourcePath = path.join(sourceDir, fileName);
            const targetPath = path.join(targetDir, fileName);
            
            // Show progress
            const percent = Math.round(((index + 1) / files.length) * 100);
            process.stdout.write(`\r    [${index + 1}/${files.length}] (${percent}%) ${fileName}...`);
            
            const content = fs.readFileSync(sourcePath, 'utf8');
            const transliterated = await transliterateMarkdown(content);
            
            fs.writeFileSync(targetPath, transliterated);
            totalFiles++;
        }
        
        // Clear progress line
        process.stdout.write('\r');
        const typeElapsed = ((Date.now() - typeStartTime) / 1000).toFixed(1);
        console.log(`  ✓ Processed ${files.length} ${type} files in ${typeElapsed}s`);
    }
    
    // Step 3: Process data.json if it exists
    const dataJsonSource = path.join(EN_US_TRANSLATIONS, 'data.json');
    const dataJsonTarget = path.join(AL_WO_TRANSLATIONS, 'data.json');
    if (fs.existsSync(dataJsonSource)) {
        console.log('\nStep 3: Processing data.json...');
        const step3StartTime = Date.now();
        const data = JSON.parse(fs.readFileSync(dataJsonSource, 'utf8'));
        const dataStrings = [];
        collectStrings(data, dataStrings);
        const dataTranslated = await transliterateStrings(dataStrings, 'data.json strings');
        const dataReconstructed = reconstruct(data, dataTranslated);
        fs.writeFileSync(dataJsonTarget, JSON.stringify(dataReconstructed, null, 2));
        const step3Elapsed = ((Date.now() - step3StartTime) / 1000).toFixed(1);
        console.log(`✓ Processed data.json (Step 3 completed in ${step3Elapsed}s)\n`);
    }
    
    const step2Elapsed = ((Date.now() - step2StartTime) / 1000).toFixed(1);
    const totalElapsed = ((Date.now() - step1StartTime) / 1000).toFixed(1);
    console.log(`=== Complete! ===`);
    console.log(`  Processed ${totalFiles} translation files (Step 2: ${step2Elapsed}s)`);
    console.log(`  Total time: ${totalElapsed}s`);
}

/**
 * Transliterates markdown text while preserving all markdown syntax.
 * Only transliterates actual text content, not markdown identifiers or URLs.
 */
async function transliterateMarkdownText(text) {
    if (!text) return text;
    
    // Use a unique placeholder that will be protected by <safe> tags
    // This ensures Java's removeCharacters doesn't strip them
    const parts = [];
    
    // Step 1: Replace in-site links #[text](url) - transliterate only text, preserve # and URL
    let processed = text.replace(/#\[([^\]]+)\]\(([^)]+)\)/g, (match, linkText, url) => {
        const placeholder = `<safe>INSITE_LINK_${parts.length}</safe>`;
        parts.push({ type: 'insite_link', linkText, url, placeholder });
        return placeholder;
    });
    
    // Step 2: Replace regular markdown links [text](url) - transliterate only text, preserve URL
    processed = processed.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (match, linkText, url) => {
        const placeholder = `<safe>LINK_${parts.length}</safe>`;
        parts.push({ type: 'link', linkText, url, placeholder });
        return placeholder;
    });
    
    // Step 3: Replace other markdown syntax with placeholders
    // Order matters: **bold** before *italic*
    processed = processed.replace(/\*\*([^*]+)\*\*/g, (match, content) => {
        const placeholder = `<safe>BOLD_${parts.length}</safe>`;
        parts.push({ type: 'bold', content, placeholder });
        return placeholder;
    });
    
    processed = processed.replace(/\*([^*\n]+)\*/g, (match, content) => {
        const placeholder = `<safe>ITALIC_${parts.length}</safe>`;
        parts.push({ type: 'italic', content, placeholder });
        return placeholder;
    });
    
    processed = processed.replace(/`([^`]+)`/g, (match, content) => {
        const placeholder = `<safe>CODE_${parts.length}</safe>`;
        parts.push({ type: 'code', content, placeholder });
        return placeholder;
    });
    
    // Handle blockquotes - match > at start of line, with or without space
    // Protect the > character and content with <safe> tags
    processed = processed.replace(/^>(\s*)(.+)$/gm, (match, spaces, content) => {
        const placeholder = `<safe>QUOTE_${parts.length}</safe>`;
        parts.push({ type: 'quote', content, spaces: spaces || ' ', placeholder });
        return placeholder;
    });
    
    // Step 4: Transliterate the remaining text (with placeholders)
    const transliterated = await transliterateString(processed);
    
    // Step 5: Restore markdown syntax with transliterated content
    let result = transliterated;
    for (let i = parts.length - 1; i >= 0; i--) {
        const part = parts[i];
        // Java may remove <safe> tags, so check for both versions
        const placeholderWithTags = part.placeholder;
        const placeholderWithoutTags = placeholderWithTags.replace(/<safe>/g, '').replace(/<\/safe>/g, '');
        
        let replacement;
        if (part.type === 'insite_link') {
            const transliteratedLinkText = await transliterateString(part.linkText);
            replacement = `#[${transliteratedLinkText}](${part.url})`;
        } else if (part.type === 'link') {
            const transliteratedLinkText = await transliterateString(part.linkText);
            replacement = `[${transliteratedLinkText}](${part.url})`;
        } else if (part.type === 'bold') {
            const transliteratedContent = await transliterateString(part.content);
            replacement = `**${transliteratedContent}**`;
        } else if (part.type === 'italic') {
            const transliteratedContent = await transliterateString(part.content);
            replacement = `*${transliteratedContent}*`;
        } else if (part.type === 'code') {
            // Don't transliterate code
            replacement = `\`${part.content}\``;
        } else if (part.type === 'quote') {
            const transliteratedContent = await transliterateString(part.content);
            replacement = `>${part.spaces}${transliteratedContent}`;
        } else {
            replacement = part.content;
        }
        
        // Try replacing with tags first, then without tags
        if (result.includes(placeholderWithTags)) {
            result = result.replace(placeholderWithTags, replacement);
        } else if (result.includes(placeholderWithoutTags)) {
            result = result.replace(placeholderWithoutTags, replacement);
        }
    }
    
    return result;
}

async function transliterateMarkdown(content) {
    const { root, sections } = parseSections(content);
    
    if (!root) {
        return content; // Can't parse, return original
    }
    
    // Transliterate the name (may contain markdown)
    const transliteratedName = await transliterateMarkdownText(root.name);
    
    // Transliterate root content
    const transliteratedRootContent = root.content.trim() 
        ? await transliterateMarkdownText(root.content.trim()) 
        : '';
    
    // Build the output
    let output = `# ${transliteratedName}\n`;
    
    if (transliteratedRootContent) {
        output += '\n' + transliteratedRootContent + '\n';
    }
    
    // Process sections
    for (const [sectionName, section] of Object.entries(sections)) {
        if (sectionName === 'metadata') {
            // Preserve metadata section as-is (it's usually structured data)
            output += `\n## Metadata\n\n${section.content}`;
        } else if (section.content.trim()) {
            const transliteratedSectionContent = await transliterateMarkdownText(section.content.trim());
            if (transliteratedSectionContent) {
                // Capitalize first letter of section name for display
                const displayName = sectionName.charAt(0).toUpperCase() + sectionName.slice(1);
                output += `\n## ${displayName}\n\n${transliteratedSectionContent}\n`;
            }
        }
    }
    
    return output;
}

function collectStrings(obj, list) {
    if (typeof obj === 'string') {
        list.push(obj);
    } else if (Array.isArray(obj)) {
        obj.forEach(item => collectStrings(item, list));
    } else if (typeof obj === 'object' && obj !== null) {
        for (const key in obj) {
            collectStrings(obj[key], list);
        }
    }
}

function reconstruct(obj, translatedList) {
    let index = 0;
    
    function walk(current) {
        if (typeof current === 'string') {
            const val = translatedList[index];
            index++;
            return val !== undefined ? val : current;
        } else if (Array.isArray(current)) {
            return current.map(item => walk(item));
        } else if (typeof current === 'object' && current !== null) {
            const res = {};
            for (const key in current) {
                res[key] = walk(current[key]);
            }
            return res;
        }
        return current;
    }
    
    return walk(obj);
}

main();
