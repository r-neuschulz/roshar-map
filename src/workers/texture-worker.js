self.onmessage = (e) => {
  const { buffer, width, height, channelNames, channels } = e.data
  const inputData = new Uint8ClampedArray(buffer)

  if (channelNames.length >= 4) {
    self.postMessage({ buffer, width, height }, [buffer])
    return
  }

  const outputLength = channelNames.length * width * height
  const output = new Uint8ClampedArray(outputLength)

  for (let i = 0; i < width * height; i++) {
    if (channels.r !== undefined) {
      output[i * channelNames.length + channels.r] = inputData[i * 4]
    }
    if (channels.g !== undefined) {
      output[i * channelNames.length + channels.g] = inputData[i * 4 + 1]
    }
    if (channels.b !== undefined) {
      output[i * channelNames.length + channels.b] = inputData[i * 4 + 2]
    }
    if (channels.a !== undefined) {
      output[i * channelNames.length + channels.a] = inputData[i * 4 + 3]
    }
  }

  self.postMessage({ buffer: output.buffer, width, height }, [output.buffer])
}
