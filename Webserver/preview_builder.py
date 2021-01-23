from sensor_processor.process_data import DataProcessor

def generate_plot_data(folder_name):
    processor = DataProcessor(folder_name)
    processor.plot_data(True)