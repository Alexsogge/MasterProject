import setuptools

with open("README.md", "r", encoding="utf-8") as fh:
    long_description = fh.read()

setuptools.setup(
    name="sensor-processor-henkela",
    version="0.2.1",
    author="Alexander Henkel",
    author_email="a.henkel@mgga.de",
    description="Analyze and plot sensor data",
    long_description=long_description,
    long_description_content_type="text/markdown",
    url="https://github.com/Alexsogge/MasterProject/tree/master/scripts",
    packages=setuptools.find_packages(),
    classifiers=[
        "Programming Language :: Python :: 3",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
    ],
    python_requires='>=3.6',
)