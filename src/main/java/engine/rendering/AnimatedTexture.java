package engine.rendering;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class AnimatedTexture extends Texture {
	private final int frames;
	private final float frameHeightFraction;
	private final float frameDuration;
	private float timer = 0f;
	private int currentFrame = 0;

	public AnimatedTexture(String filepath, float frameDurationSeconds) {
		super(filepath);
		int detectedFrames = Math.max(1, height / Math.max(1, width));
		this.frames = detectedFrames;
		this.frameHeightFraction = 1.0f / (float) frames;
		this.frameDuration = Math.max(0.01f, frameDurationSeconds);
	}

	public void update(float deltaSeconds) {
		if (frames <= 1)
			return;
		timer += deltaSeconds;
		while (timer >= frameDuration) {
			timer -= frameDuration;
			currentFrame = (currentFrame + 1) % frames;
		}
	}

	@Override
	public void bind() {
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		if (id != 0)
			GL30.glBindTexture(GL30.GL_TEXTURE_2D, id);
		int currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		if (currentProgram != 0) {
			int totalHeight = this.height;
			int framePixels = this.width;
			if (totalHeight <= 0 || framePixels <= 0)
				return;
			float frameHeightFraction = (float) framePixels / (float) totalHeight;
			float texel = 1.0f / (float) totalHeight;
			float pad = 0.5f * texel;
			float offset = currentFrame * frameHeightFraction + pad;
			float scale = frameHeightFraction - 2.0f * pad;
			int offLoc = GL20.glGetUniformLocation(currentProgram, "uFrameOffset");
			int scaleLoc = GL20.glGetUniformLocation(currentProgram, "uFrameScale");
			if (offLoc >= 0)
				GL20.glUniform1f(offLoc, offset);
			if (scaleLoc >= 0)
				GL20.glUniform1f(scaleLoc, scale);
		}
	}

	public int getFrames() {
		return frames;
	}

	public int getCurrentFrame() {
		return currentFrame;
	}

	public float getFrameHeightFraction() {
		return frameHeightFraction;
	}
}